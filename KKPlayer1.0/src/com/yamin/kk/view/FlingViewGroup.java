package com.yamin.kk.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

public class FlingViewGroup extends ViewGroup {
    public static final String TAG = "KKY/FlingViewGroup";
    private final static int TOUCH_STATE_MOVE = 0;
    private final static int TOUCH_STATE_REST = 1;

    private int mCurrentView = 0;
    private final Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private int mTouchState = TOUCH_STATE_REST;
    private int mInterceptTouchState = TOUCH_STATE_REST;
    private final int mTouchSlop;
    private final int mMaximumVelocity;

    private float mLastX;
    private float mLastInterceptDownY;
    private float mInitialMotionX;
    private float mInitialMotionY;

    private ViewSwitchListener mViewSwitchListener;

    public FlingViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mScroller = new Scroller(context);
        ViewConfiguration config = ViewConfiguration.get(getContext());
        mTouchSlop = config.getScaledTouchSlop();
        mMaximumVelocity = config.getScaledMaximumFlingVelocity();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childLeft = 0;

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = child.getMeasuredWidth();
                child.layout(childLeft, 0, childLeft + childWidth,
                        child.getMeasuredHeight());
                childLeft += childWidth;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY ) {
            throw new IllegalStateException("can only be used in EXACTLY mode.");
        }

        final int count = getChildCount();
        int maxHeight = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.measure(widthMeasureSpec, heightMeasureSpec);
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
        }

        setMeasuredDimension(getMeasuredWidth(), maxHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (!mScroller.isFinished())
            mScroller.abortAnimation();
        super.onSizeChanged(w, h, oldw, oldh);
        scrollTo(mCurrentView * w, 0);
        requestLayout();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    public int getPosition() {
        return mCurrentView;
    }

    public void setPosition(int position) {
        mCurrentView = position;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (getChildCount() == 0)
            return false;

        final float x = ev.getX();
        final float y = ev.getY();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = x;
                mLastInterceptDownY = ev.getY();
                mTouchState = mScroller.isFinished() ?
                        TOUCH_STATE_REST : TOUCH_STATE_MOVE;
                mInterceptTouchState = TOUCH_STATE_REST;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mInterceptTouchState == TOUCH_STATE_MOVE)
                    return false;
                if (Math.abs(mLastInterceptDownY - y) > mTouchSlop)
                    mInterceptTouchState = TOUCH_STATE_MOVE;
                if (Math.abs(mLastX - x) > mTouchSlop)
                    mTouchState = TOUCH_STATE_MOVE;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mInterceptTouchState = TOUCH_STATE_REST;
                break;
        }

        return mTouchState == TOUCH_STATE_MOVE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getChildCount() == 0)
            return false;

        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);

        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotionX = x;
                mInitialMotionY = y;
                if (!mScroller.isFinished())
                    mScroller.abortAnimation();
                mLastX = x;
                if (mViewSwitchListener != null)
                    mViewSwitchListener.onTouchDown();
                break;
            case MotionEvent.ACTION_MOVE:
                int delta = (int) (mLastX - x);
                mLastX = x;
                final int scrollX = getScrollX();
                if (delta < 0) {
                    if (scrollX > 0) {
                        scrollBy(Math.max(-scrollX, delta), 0);
                    }
                } else if (delta > 0) {
                    final int availableToScroll =
                            getChildAt(getChildCount() - 1).getRight() - scrollX - getWidth();
                    if (availableToScroll > 0) {
                        scrollBy(Math.min(availableToScroll, delta), 0);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity();

                if (velocityX > 1000 && mCurrentView > 0) {
                    snapToScreen(mCurrentView - 1);
                } else if (velocityX < -1000
                        && mCurrentView < getChildCount() - 1) {
                    snapToScreen(mCurrentView + 1);
                } else {
                    snapToDestination();
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                if (mViewSwitchListener != null) {
                    mViewSwitchListener.onTouchUp();
                    final float dx = x - mInitialMotionX;
                    final float dy = y - mInitialMotionY;
                    ViewConfiguration config = ViewConfiguration.get(getContext());
                    final int slop = config.getScaledTouchSlop();
                    if (dx * dx + dy * dy < slop * slop)
                        mViewSwitchListener.onTouchClick();
                }

                break;
        }

        return true;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mViewSwitchListener != null) {
            float progress = (float) l / (float) (getWidth() * (getChildCount() - 1));
            if (l != mCurrentView * getWidth())
                mViewSwitchListener.onSwitching(progress);
            else
                mViewSwitchListener.onSwitched(mCurrentView);
        }
    }

    private void snapToDestination() {
        final int screenWidth = getWidth();
        final int whichScreen = (getScrollX() + (screenWidth / 2)) / screenWidth;
        snapToScreen(whichScreen);
    }

    public void snapToScreen(int position) {
        mCurrentView = position;
        final int delta = (position * getWidth()) - getScrollX();
        mScroller.startScroll(getScrollX(), 0, delta, 0, Math.abs(delta));
        invalidate();
    }

    public void scrollTo(int position) {
        mCurrentView = position;
        final int delta = (position * getWidth()) - getScrollX();
        mScroller.startScroll(getScrollX(), 0, delta, 0, 0);
        invalidate();
    }

    public void smoothScrollTo(int position) {
        mCurrentView = position;
        final int delta = (position * getWidth()) - getScrollX();
        mScroller.startScroll(getScrollX(), 0, delta, 0, 300);
        invalidate();
    }

    public void setOnViewSwitchedListener(ViewSwitchListener l) {
        mViewSwitchListener = l;
    }

    public static interface ViewSwitchListener {
        void onSwitching(float progress);

        void onSwitched(int position);

        void onTouchDown();

        void onTouchUp();

        void onTouchClick();
    }

}
