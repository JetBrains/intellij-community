// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    setAutoRequestFocus(false);
    setSize(size);
    setLocationInTheCenterOfScreen();
  }

  public void initAndShow() {
    initImages();

    StartUpMeasurer.addInstantEvent("splash shown");
    Activity activity = StartUpMeasurer.startActivity("splash set visible");
    setVisible(true);
    activity.end();
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

  @Override
  public void paint(Graphics g) {
    if (myProgress < 0.10 || myProgressSlideImages.isEmpty()) {
      StartupUiUtil.drawImage(g, myImage, 0, 0, null);
    }

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
      Graphics graphics = getGraphics();
      // not yet initialized
      if (graphics != null) {
        paintProgress(graphics);
      }
    }
  }

  private void paintProgress(@Nullable Graphics g) {
    if (g == null) return;

    boolean hasSlides = !myProgressSlideImages.isEmpty();
    if (hasSlides) {
      paintSlides(g);
    }

    Color color = myInfo.getProgressColor();
    if (color == null) {
      return;
    }

    int progressWidth = (int)(myWidth * myProgress);
    int currentWidth = progressWidth - myProgressLastPosition;
    if (currentWidth == 0) {
      return;
    }

    g.setColor(color);
    int y = hasSlides ? myHeight - myProgressHeight : myProgressY;
    g.fillRect(myProgressLastPosition, y, currentWidth, myProgressHeight);
    if (myProgressTail != null) {
      int tx = (int)(currentWidth - (myProgressTail.getIconWidth() / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE));
      int ty = (int)(myProgressY - (myProgressTail.getIconHeight() - myProgressHeight) / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE);
      myProgressTail.paintIcon(this, g, tx, ty);
    }
    myProgressLastPosition = progressWidth;
  }

  private void paintSlides(@NotNull Graphics g) {
    for (ProgressSlideAndImage progressSlide : myProgressSlideImages) {
      if (progressSlide.slide.getProgressRation() <= myProgress) {
        if (progressSlide.isDrawn) {
          continue;
        }
        StartupUiUtil.drawImage(g, progressSlide.image, 0, 0, null);
        progressSlide.isDrawn = true;
      }
    }
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