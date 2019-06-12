// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
public final class Splash extends Window {
  private static final float JBUI_INIT_SCALE = JBUIScale.scale(1f);

  private final ApplicationInfoEx myInfo;
  private final int myWidth;
  private final int myHeight;
  private final int myProgressHeight;
  private final int myProgressY;
  private double myProgress;
  private int myProgressLastPosition = 0;
  private final Icon myProgressTail;
  private final List<ProgressSlideAndImage> myProgressSlideImages = new ArrayList<>();
  private final Image myImage;

  private final NotNullLazyValue<Font> myFont = createFont();

  public Splash(@NotNull ApplicationInfoEx info) {
    super(null);

    myInfo = info;
    myProgressHeight = uiScale(info.getProgressHeight());
    myProgressY = uiScale(info.getProgressY());
    myProgressTail = info.getProgressTailIcon();

    setFocusableWindowState(false);

    myImage = loadImage(info.getSplashImageUrl());
    myWidth = myImage.getWidth(null);
    myHeight = myImage.getHeight(null);
    Dimension size = new Dimension(myWidth, myHeight);
    if (Boolean.getBoolean("suppress.focus.stealing") && Boolean.getBoolean("suppress.focus.stealing.auto.request.focus")) {
      setAutoRequestFocus(false);
    }
    setSize(size);
    setLocationInTheCenterOfScreen();

    initImages();

    setVisible(true);
    paint(getGraphics());
    toFront();
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @NotNull
  private static Image loadImage(@NotNull String path) {
    Image result = ImageLoader.loadFromUrl(path, Splash.class, ImageLoader.ALLOW_FLOAT_SCALING, null, ScaleContext.create());
    if (result == null) {
      throw new IllegalStateException("Cannot find image: " + path);
    }
    return result;
  }

  @NotNull
  public static NotNullLazyValue<Font> createFont() {
    return NotNullLazyValue.createValue(() -> {
      Font font;
      if (SystemInfo.isMacOSElCapitan) {
        font = createFont(".SF NS Text");
      }
      else {
        //noinspection SpellCheckingInspection
        font = SystemInfo.isMacOSYosemite ? createFont("HelveticaNeue-Regular") : null;
      }

      if (font == null || UIUtil.isDialogFont(font)) {
        font = createFont(UIUtil.ARIAL_FONT_NAME);
      }
      return font;
    });
  }

  @Override
  public void paint(Graphics g) {
    StartupUiUtil.drawImage(g, myImage, 0, 0, null);
    paintProgress(g);
  }

  private void initImages() {
    List<ProgressSlide> progressSlides = myInfo.getProgressSlides();
    if (progressSlides.isEmpty()) {
      return;
    }

    for (ProgressSlide progressSlide : progressSlides) {
      myProgressSlideImages.add(new ProgressSlideAndImage(progressSlide, loadImage(progressSlide.getUrl())));
    }
    myProgressSlideImages.sort(Comparator.comparing(t -> t.slide.getProgressRation()));
  }

  private void setLocationInTheCenterOfScreen() {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    if (SystemInfo.isWindows) {
      JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(getGraphicsConfiguration()));
    }
    setLocation(StartupUiUtil.getCenterPoint(bounds, getSize()));
  }

  public void showProgress(double progress) {
    if (myInfo.getProgressColor() == null) {
      return;
    }

    if (((progress - myProgress) > 0.01) || (progress > 0.99)) {
      myProgress = progress;
      paintProgress(getGraphics());
    }
  }

  private void paintProgress(@NotNull Graphics g) {
    boolean hasSlides = !myProgressSlideImages.isEmpty();
    if (hasSlides) {
      paintSlides(g);
    }

    Color color = myInfo.getProgressColor();
    if (color == null) {
      return;
    }

    final int progressWidth = (int)(myWidth * myProgress);
    int currentWidth = progressWidth - myProgressLastPosition;
    if (currentWidth == 0) {
      return;
    }

    g.setColor(color);
    int y = hasSlides ? myHeight - myProgressHeight : myProgressY;
    g.fillRect(myProgressLastPosition, y, currentWidth, myProgressHeight);
    if (myProgressTail != null) {
      float onePixel = JBUI_INIT_SCALE;
      myProgressTail.paintIcon(this, g, (int)(currentWidth - (myProgressTail.getIconWidth() / onePixel / 2f * onePixel)),
                               (int)(myProgressY - (myProgressTail.getIconHeight() - myProgressHeight) / onePixel / 2f * onePixel)); //I'll buy you a beer if you understand this line without playing with it
    }
    myProgressLastPosition = progressWidth;
  }

  private void paintSlides(@NotNull Graphics g) {
    for (ProgressSlideAndImage progressSlide : myProgressSlideImages) {
      if (progressSlide.slide.getProgressRation() <= myProgress) {
        if(progressSlide.isDrawn)
          continue;

        StartupUiUtil.drawImage(g, progressSlide.image, 0, 0, null);
        progressSlide.isDrawn = true;
      }
    }
  }

  public void paintLicenseeInfo() {
    showLicenseeInfo(getGraphics(), 0, 0, myHeight, myInfo, myFont);
  }

  public static boolean showLicenseeInfo(@NotNull Graphics g, int x, int y, final int height, @NotNull ApplicationInfoEx info, @NotNull NotNullLazyValue<? extends Font> font) {
    if (!info.showLicenseeInfo()) {
      return false;
    }

    LicensingFacade provider = LicensingFacade.getInstance();
    if (provider == null) {
      return true;
    }

    String licensedToMessage = provider.getLicensedToMessage();
    List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();
    if (licensedToMessage == null && licenseRestrictionsMessages.isEmpty()) {
      return true;
    }

    UIUtil.applyRenderingHints(g);
    g.setFont(font.getValue());
    g.setColor(info.getSplashTextColor());

    int offsetX = Math.max(uiScale(15), uiScale(info.getLicenseOffsetX()));
    int offsetYUnscaled = info.getLicenseOffsetY();

    if (licensedToMessage != null) {
      g.drawString(licensedToMessage, x + offsetX, y + height - uiScale(offsetYUnscaled));
    }

    if (!licenseRestrictionsMessages.isEmpty()) {
      g.drawString(licenseRestrictionsMessages.get(0), x + offsetX, y + height - uiScale(offsetYUnscaled - 16));
    }
    return true;
  }

  @NotNull
  private static Font createFont(String name) {
    return new Font(name, Font.PLAIN, uiScale(12));
  }

  private static int uiScale(int i) {
    return (int)(i * JBUI_INIT_SCALE);
  }
}

final class ProgressSlideAndImage {
  public final ProgressSlide slide;
  public final Image image;

  ProgressSlideAndImage(@NotNull ProgressSlide slide, @NotNull Image image) {
    this.slide = slide;
    this.image = image;
  }

  boolean isDrawn = false;
}