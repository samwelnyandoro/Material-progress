package com.materialprogress.mylibrary;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

public class ProgressWheel extends View {
    private final int barLength = 16;
    private int circleRadius = 28;
    private int barWidth = 4;
    private int rimWidth = 4;
    private boolean fillRadius = false;
    private double timeStartGrowing = 0;
    private double barSpinCycleTime = 460;
    private float barExtraLength = 0;
    private boolean barGrowingFromFront = true;
    private long pausedTimeWithoutGrowing = 0;
    private int barColor = 0xAA000000;
    private int rimColor = 0x00FFFFFF;

    //Paints
    private final Paint barPaint = new Paint();
    private final Paint rimPaint = new Paint();

    private RectF circleBounds = new RectF();
    private float spinSpeed = 230.0f;
    private long lastTimeAnimated = 0;
    private boolean linearProgress;
    private float mProgress = 0.0f;
    private float mTargetProgress = 0.0f;
    private boolean isSpinning = false;
    private ProgressCallback callback;
    private boolean shouldAnimate;

    public ProgressWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ProgressWheel(Context context) {
        super(context);
        setAnimationEnabled();
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProgressWheel);
        parseAttributes(a);
        a.recycle();
        setAnimationEnabled();
    }

    private void setAnimationEnabled() {
        float animationValue;
        animationValue = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1);

        shouldAnimate = animationValue != 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int viewWidth = circleRadius * 2 + this.getPaddingLeft() + this.getPaddingRight();
        int viewHeight = circleRadius * 2 + this.getPaddingTop() + this.getPaddingBottom();
        int width = resolveSize(viewWidth, widthMeasureSpec);
        int height = resolveSize(viewHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupBounds(w, h);
        setupPaints();
        invalidate();
    }

    private void setupPaints() {
        barPaint.setColor(barColor);
        barPaint.setAntiAlias(true);
        barPaint.setStyle(Style.STROKE);
        barPaint.setStrokeWidth(barWidth);
        rimPaint.setColor(rimColor);
        rimPaint.setAntiAlias(true);
        rimPaint.setStyle(Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
    }

    private void setupBounds(int layout_width, int layout_height) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        if (!fillRadius) {
            int minValue = Math.min(layout_width - paddingLeft - paddingRight,
                    layout_height - paddingBottom - paddingTop);
            int circleDiameter = Math.min(minValue, circleRadius * 2 - barWidth * 2);
            int xOffset = (layout_width - paddingLeft - paddingRight - circleDiameter) / 2 + paddingLeft;
            int yOffset = (layout_height - paddingTop - paddingBottom - circleDiameter) / 2 + paddingTop;
            circleBounds =
                    new RectF(xOffset + barWidth, yOffset + barWidth, xOffset + circleDiameter - barWidth,
                            yOffset + circleDiameter - barWidth);
        } else {
            circleBounds = new RectF(paddingLeft + barWidth, paddingTop + barWidth,
                    layout_width - paddingRight - barWidth, layout_height - paddingBottom - barWidth);
        }
    }

    private void parseAttributes(TypedArray a) {
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        barWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barWidth, metrics);
        rimWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, rimWidth, metrics);
        circleRadius =
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, circleRadius, metrics);
        circleRadius =
                (int) a.getDimension(R.styleable.ProgressWheel_matProg_circleRadius, circleRadius);
        fillRadius = a.getBoolean(R.styleable.ProgressWheel_matProg_fillRadius, false);
        barWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_barWidth, barWidth);
        rimWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_rimWidth, rimWidth);
        float baseSpinSpeed =
                a.getFloat(R.styleable.ProgressWheel_matProg_spinSpeed, spinSpeed / 360.0f);
        spinSpeed = baseSpinSpeed * 360;
        barSpinCycleTime =
                a.getInt(R.styleable.ProgressWheel_matProg_barSpinCycleTime, (int) barSpinCycleTime);
        barColor = a.getColor(R.styleable.ProgressWheel_matProg_barColor, barColor);
        rimColor = a.getColor(R.styleable.ProgressWheel_matProg_rimColor, rimColor);
        linearProgress = a.getBoolean(R.styleable.ProgressWheel_matProg_linearProgress, false);
        if (a.getBoolean(R.styleable.ProgressWheel_matProg_progressIndeterminate, false)) {
            spin();
        }
    }

    public void setCallback(ProgressCallback progressCallback) {
        callback = progressCallback;
        if (!isSpinning) {
            runCallback();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(circleBounds, 360, 360, false, rimPaint);
        boolean mustInvalidate = false;
        if (!shouldAnimate) {
            return;
        }
        if (isSpinning) {
            mustInvalidate = true;
            long deltaTime = (SystemClock.uptimeMillis() - lastTimeAnimated);
            float deltaNormalized = deltaTime * spinSpeed / 1000.0f;
            updateBarLength(deltaTime);
            mProgress += deltaNormalized;
            if (mProgress > 360) {
                mProgress -= 360f;
                runCallback(-1.0f);
            }
            lastTimeAnimated = SystemClock.uptimeMillis();
            float from = mProgress - 90;
            float length = barLength + barExtraLength;
            if (isInEditMode()) {
                from = 0;
                length = 135;
            }
            canvas.drawArc(circleBounds, from, length, false, barPaint);
        } else {
            float oldProgress = mProgress;
            if (mProgress != mTargetProgress) {
                mustInvalidate = true;
                float deltaTime = (float) (SystemClock.uptimeMillis() - lastTimeAnimated) / 1000;
                float deltaNormalized = deltaTime * spinSpeed;
                mProgress = Math.min(mProgress + deltaNormalized, mTargetProgress);
                lastTimeAnimated = SystemClock.uptimeMillis();
            }
            if (oldProgress != mProgress) {
                runCallback();
            }
            float offset = 0.0f;
            float progress = mProgress;
            if (!linearProgress) {
                float factor = 2.0f;
                offset = (float) (1.0f - Math.pow(1.0f - mProgress / 360.0f, 2.0f * factor)) * 360.0f;
                progress = (float) (1.0f - Math.pow(1.0f - mProgress / 360.0f, factor)) * 360.0f;
            }
            if (isInEditMode()) {
                progress = 360;
            }
            canvas.drawArc(circleBounds, offset - 90, progress, false, barPaint);
        }
        if (mustInvalidate) {
            invalidate();
        }
    }

    private void updateBarLength(long deltaTimeInMilliSeconds) {
        long pauseGrowingTime = 200;
        if (pausedTimeWithoutGrowing >= pauseGrowingTime) {
            timeStartGrowing += deltaTimeInMilliSeconds;
            if (timeStartGrowing > barSpinCycleTime) {
                timeStartGrowing -= barSpinCycleTime;
                pausedTimeWithoutGrowing = 0;
                barGrowingFromFront = !barGrowingFromFront;
            }
            float distance =
                    (float) Math.cos((timeStartGrowing / barSpinCycleTime + 1) * Math.PI) / 2
                            + 0.5f;
            float destLength = (barLength - barExtraLength);
            if (barGrowingFromFront) {
                barExtraLength = distance * destLength;
            } else {
                float newLength = destLength * (1 - distance);
                mProgress += (barExtraLength - newLength);
                barExtraLength = newLength;
            }
        } else {
            pausedTimeWithoutGrowing += deltaTimeInMilliSeconds;
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mProgress = savedState.mProgress;
        mTargetProgress = savedState.mTargetProgress;
        isSpinning = savedState.isSpinning;
        spinSpeed = savedState.spinSpeed;
        barWidth = savedState.barWidth;
        barColor = savedState.barColor;
        rimWidth = savedState.rimWidth;
        rimColor = savedState.rimColor;
        linearProgress = savedState.linearProgress;
        fillRadius = savedState.fillRadius;
        circleRadius = savedState.circleRadius;
        timeStartGrowing = savedState.timeStartGrowing;
        barSpinCycleTime = savedState.barSpinCycleTime;
        barExtraLength = savedState.barExtraLength;
        barGrowingFromFront = savedState.barGrowingFromFront;
        pausedTimeWithoutGrowing = savedState.pausedTimeWithoutGrowing;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.mProgress = mProgress;
        savedState.mTargetProgress = mTargetProgress;
        savedState.isSpinning = isSpinning;
        savedState.spinSpeed = spinSpeed;
        savedState.barWidth = barWidth;
        savedState.barColor = barColor;
        savedState.rimWidth = rimWidth;
        savedState.rimColor = rimColor;
        savedState.linearProgress = linearProgress;
        savedState.fillRadius = fillRadius;
        savedState.circleRadius = circleRadius;
        savedState.timeStartGrowing = timeStartGrowing;
        savedState.barSpinCycleTime = barSpinCycleTime;
        savedState.barExtraLength = barExtraLength;
        savedState.barGrowingFromFront = barGrowingFromFront;
        savedState.pausedTimeWithoutGrowing = pausedTimeWithoutGrowing;
        return savedState;
    }

    public void resetCount() {
        mProgress = 0.0f;
        mTargetProgress = 0.0f;
        invalidate();
    }

    public void stopSpinning() {
        isSpinning = false;
        mProgress = 0.0f;
        mTargetProgress = 0.0f;
        invalidate();
    }

    public void spin() {
        lastTimeAnimated = SystemClock.uptimeMillis();
        isSpinning = true;
        invalidate();
    }

    public void setInstantProgress(float progress) {
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;
        }
        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }
        if (progress == mTargetProgress) {
            return;
        }
        mTargetProgress = Math.min(progress * 360.0f, 360.0f);
        mProgress = mTargetProgress;
        lastTimeAnimated = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setProgress(float progress) {
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;
            runCallback();
        }
        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }
        if (progress == mTargetProgress) {
            return;
        }
        if (mProgress == mTargetProgress) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }
        mTargetProgress = Math.min(progress * 360.0f, 360.0f);
        invalidate();
    }

    public void setLinearProgress(boolean isLinear) {
        linearProgress = isLinear;
        if (!isSpinning) {
            invalidate();
        }
    }

    public void setCircleRadius(int circleRadius) {
        this.circleRadius = circleRadius;
        if (!isSpinning) {
            invalidate();
        }
    }

    public int getCircleRadius() {
        return circleRadius;
    }

    public void setBarWidth(int barWidth) {
        this.barWidth = barWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    public int getBarWidth() {
        return barWidth;
    }

    public void setBarColor(int barColor) {
        this.barColor = barColor;
        if (!isSpinning) {
            invalidate();
        }
    }

    public int getBarColor() {
        return barColor;
    }

    public void setRimWidth(int rimWidth) {
        this.rimWidth = rimWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    public int getRimWidth() {
        return rimWidth;
    }

    public void setRimColor(int rimColor) {
        this.rimColor = rimColor;
        if (!isSpinning) {
            invalidate();
        }
    }

    public int getRimColor() {
        return rimColor;
    }

    public void setSpinSpeed(float spinSpeed) {
        this.spinSpeed = spinSpeed;
    }

    public float getSpinSpeed() {
        return spinSpeed;
    }

    public void setFillRadius(boolean fillRadius) {
        this.fillRadius = fillRadius;
        if (!isSpinning) {
            invalidate();
        }
    }

    public boolean isFillRadius() {
        return fillRadius;
    }

    @Override
    public void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }
    }

    private void runCallback() {
        runCallback(mProgress / 360.0f);
    }

    private void runCallback(float value) {
        if (callback != null) {
            callback.onProgressUpdate(value);
        }
    }

    public interface ProgressCallback {
        void onProgressUpdate(float progress);
    }

    static class SavedState extends BaseSavedState {
        float mProgress;
        float mTargetProgress;
        boolean isSpinning;
        float spinSpeed;
        int barWidth;
        int barColor;
        int rimWidth;
        int rimColor;
        boolean linearProgress;
        boolean fillRadius;
        int circleRadius;
        double timeStartGrowing;
        double barSpinCycleTime;
        float barExtraLength;
        boolean barGrowingFromFront;
        long pausedTimeWithoutGrowing;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mProgress = in.readFloat();
            mTargetProgress = in.readFloat();
            isSpinning = in.readByte() != 0;
            spinSpeed = in.readFloat();
            barWidth = in.readInt();
            barColor = in.readInt();
            rimWidth = in.readInt();
            rimColor = in.readInt();
            linearProgress = in.readByte() != 0;
            fillRadius = in.readByte() != 0;
            circleRadius = in.readInt();
            timeStartGrowing = in.readDouble();
            barSpinCycleTime = in.readDouble();
            barExtraLength = in.readFloat();
            barGrowingFromFront = in.readByte() != 0;
            pausedTimeWithoutGrowing = in.readLong();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(mProgress);
            dest.writeFloat(mTargetProgress);
            dest.writeByte((byte) (isSpinning ? 1 : 0));
            dest.writeFloat(spinSpeed);
            dest.writeInt(barWidth);
            dest.writeInt(barColor);
            dest.writeInt(rimWidth);
            dest.writeInt(rimColor);
            dest.writeByte((byte) (linearProgress ? 1 : 0));
            dest.writeByte((byte) (fillRadius ? 1 : 0));
            dest.writeInt(circleRadius);
            dest.writeDouble(timeStartGrowing);
            dest.writeDouble(barSpinCycleTime);
            dest.writeFloat(barExtraLength);
            dest.writeByte((byte) (barGrowingFromFront ? 1 : 0));
            dest.writeLong(pausedTimeWithoutGrowing);
        }

        @NonNull
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(@NonNull Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
