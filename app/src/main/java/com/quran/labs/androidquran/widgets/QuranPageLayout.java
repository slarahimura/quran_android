package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.Px;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class QuranPageLayout extends ViewGroup
    implements ObservableScrollView.OnScrollListener {

  @IntDef( { BorderMode.HIDDEN, BorderMode.LIGHT, BorderMode.DARK, BorderMode.LINE } )
  @interface BorderMode {
    int HIDDEN = 0;
    int LIGHT = 1;
    int DARK = 2;
    int LINE = 3;
  }

  private static PaintDrawable leftGradient;
  private static PaintDrawable rightGradient;
  private static int gradientForNumberOfPages;
  private static boolean areGradientsLandscape;
  private static BitmapDrawable leftPageBorder;
  private static BitmapDrawable rightPageBorder;
  private static BitmapDrawable leftPageBorderNight;
  private static BitmapDrawable rightPageBorderNight;

  private static int lineColor;
  private static ShapeDrawable lineDrawable;

  protected Context context;
  protected PageController pageController;
  protected int pageNumber;

  private boolean isNightMode;
  private ObservableScrollView scrollView;
  private @BorderMode int leftBorder;
  private @BorderMode int rightBorder;
  private View errorLayout;
  private TextView errorText;
  private View innerView;
  private int viewPaddingSmall;
  private int viewPaddingLarge;

  public QuranPageLayout(Context context) {
    super(context);
    this.context = context;
    ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
    Resources resources = context.getResources();
    final boolean isLandscape =
        resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    innerView = generateContentView(context, isLandscape);
    viewPaddingSmall = resources.getDimensionPixelSize(R.dimen.page_margin_small);
    viewPaddingLarge = resources.getDimensionPixelSize(R.dimen.page_margin_large);

    LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    if (isLandscape && shouldWrapWithScrollView()) {
      scrollView = new ObservableScrollView(context);
      scrollView.setFillViewport(true);
      addView(scrollView, lp);
      scrollView.addView(innerView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      scrollView.setOnScrollListener(this);
    } else {
      addView(innerView, lp);
    }

    if (areGradientsLandscape != isLandscape) {
      leftGradient = null;
      rightGradient = null;
      areGradientsLandscape = isLandscape;
    }

    if (lineDrawable == null) {
      lineDrawable = new ShapeDrawable(new RectShape());
      lineDrawable.setIntrinsicWidth(1);
      lineDrawable.setIntrinsicHeight(1);

      // these bitmaps are 11x1, so fairly small to keep both day and night versions around
      leftPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_left));
      leftPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_left_border));
      rightPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_right));
      rightPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_right_border));
    }
    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    View view = resolveView();
    if (view != null) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      int leftLineWidth = leftBorder == BorderMode.LINE ? 1 : leftPageBorder.getIntrinsicWidth();
      int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : rightPageBorder.getIntrinsicWidth();
      int headerFooterHeight = 0;
      width = width - (leftLineWidth + rightLineWidth + viewPaddingSmall + viewPaddingLarge);
      height = height - 2 * headerFooterHeight;
      view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      if (errorLayout != null) {
        errorLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    View view = resolveView();
    if (view != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      @Px int leftLineWidth = leftBorder == BorderMode.LINE ?
          1 : leftPageBorder.getIntrinsicWidth();
      @Px int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : rightPageBorder.getIntrinsicWidth();
      int headerFooterHeight = 0;
      view.layout(leftLineWidth, headerFooterHeight,
          width - rightLineWidth, height - headerFooterHeight);
      if (errorLayout != null) {
        int errorLayoutWidth = errorLayout.getMeasuredWidth();
        int errorLayoutHeight = errorLayout.getMeasuredHeight();
        int leftRightOffset = ((width - (leftLineWidth + rightLineWidth)) - errorLayoutWidth) / 2;
        int topBottomOffset = ((height - 2 * headerFooterHeight) - errorLayoutHeight) / 2;
        errorLayout.layout(leftLineWidth + leftRightOffset,
            headerFooterHeight + topBottomOffset,
            leftLineWidth + leftRightOffset + errorLayoutWidth,
            headerFooterHeight + topBottomOffset + errorLayoutHeight);
      }
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int width = getWidth();
    if (width > 0) {
      int height = getHeight();
      Drawable left = leftBorder == BorderMode.LINE ? lineDrawable :
          leftBorder == BorderMode.LIGHT ? leftPageBorder : leftPageBorderNight;
      left.setBounds(0, 0, left.getIntrinsicWidth(), height);
      left.draw(canvas);

      if (rightBorder != BorderMode.HIDDEN) {
        Drawable right = rightBorder == BorderMode.LIGHT ? rightPageBorder : rightPageBorderNight;
        right.setBounds(width - right.getIntrinsicWidth(), 0, width, height);
        right.draw(canvas);
      }
    }
  }

  protected abstract View generateContentView(Context context, boolean isLandscape);
  protected abstract void setContentNightMode(boolean nightMode, int textBrightness);

  protected boolean shouldWrapWithScrollView() {
    return true;
  }

  private View resolveView() {
    return scrollView != null ? scrollView : innerView;
  }

  public void setPageController(PageController controller, int pageNumber) {
    this.pageNumber = pageNumber;
    this.pageController = controller;
  }

  public void updateView(boolean nightMode, boolean useNewBackground, int pagesVisible) {
    if (rightGradient == null || gradientForNumberOfPages != pagesVisible) {
      final WindowManager mgr =
          (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
      Display display = mgr.getDefaultDisplay();
      int width = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
          QuranDisplayHelper.getWidthKitKat(display) : display.getWidth();
      width = width / pagesVisible;
      leftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      rightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      gradientForNumberOfPages = pagesVisible;
    }

    isNightMode = nightMode;
    int lineColor = Color.BLACK;
    final int nightModeTextBrightness = nightMode ?
        QuranSettings.getInstance(context).getNightModeTextBrightness() :
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (nightMode) {
      lineColor = Color.argb(nightModeTextBrightness, 255, 255, 255);
    }

    if (pageNumber % 2 == 0) {
      leftBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      rightBorder = BorderMode.HIDDEN;
    } else {
      rightBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      if (QuranPageLayout.lineColor != lineColor) {
        QuranPageLayout.lineColor = lineColor;
        lineDrawable.getPaint().setColor(lineColor);
      }
      leftBorder = BorderMode.LINE;
    }
    setContentNightMode(nightMode, nightModeTextBrightness);

    if (nightMode) {
      setBackgroundColor(Color.BLACK);
    } else if (useNewBackground) {
      setBackgroundDrawable((pageNumber % 2 == 0 ? leftGradient : rightGradient));
    } else {
      setBackgroundColor(ContextCompat.getColor(context, R.color.page_background));
    }

    if (errorText != null) {
      updateErrorTextColor();
    }
  }

  public void showError(@StringRes int errorRes) {
    if (errorLayout == null) {
      inflateErrorLayout();
    }
    errorLayout.setVisibility(VISIBLE);
    errorText.setText(errorRes);
  }

  private void inflateErrorLayout() {
    final LayoutInflater inflater = LayoutInflater.from(context);
    errorLayout = inflater.inflate(R.layout.page_load_error, this, false);
    LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    addView(errorLayout, lp);
    errorText = (TextView) errorLayout.findViewById(R.id.reason_text);
    final Button button = (Button) errorLayout.findViewById(R.id.retry_button);
    updateErrorTextColor();
    button.setOnClickListener(v -> {
      errorLayout.setVisibility(GONE);
      if (pageController != null) {
        pageController.handleRetryClicked();
      }
    });
  }

  private void updateErrorTextColor() {
    errorText.setTextColor(isNightMode ? Color.WHITE : Color.BLACK);
  }

  public int getCurrentScrollY() {
    return scrollView == null ? 0 : scrollView.getScrollY();
  }

  public boolean canScroll() {
    return scrollView != null;
  }

  public void smoothScrollLayoutTo(int y) {
    scrollView.smoothScrollTo(scrollView.getScrollX(), y);
  }

  @Override
  public void onScrollChanged(ObservableScrollView scrollView,
      int x, int y, int oldx, int oldy) {
    if (pageController != null) {
      pageController.onScrollChanged(x, y, oldx, oldy);
    }
  }
}
