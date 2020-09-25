// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
public final class Splash extends Window {
  private static final float JBUI_INIT_SCALE = JBUIScale.scale(1f);

  private final int myWidth;
  private final int myHeight;
  private final int myProgressHeight;
  private final int myProgressY;
  private double myProgress;
  private final Color myProgressColor;
  private int myProgressLastPosition = 0;
  private final Icon myProgressTail;
  private final @Nullable ProgressSlidePainter myProgressSlidePainter;
  private final Image myImage;

  public Splash(@NotNull ApplicationInfoEx info) {
    super(null);

    myProgressSlidePainter = info.getProgressSlides().isEmpty() ? null : new ProgressSlidePainter(info);
    myProgressHeight = uiScale(info.getProgressHeight());
    myProgressY = uiScale(info.getProgressY());

    myProgressTail = getProgressTailIcon(info);

    setFocusableWindowState(false);

    myImage = loadImage(info.getSplashImageUrl());
    myWidth = myImage.getWidth(null);
    myHeight = myImage.getHeight(null);
    long rgba = info.getProgressColor();
    //noinspection UseJBColor
    myProgressColor = rgba == -1 ? null :new Color((int)rgba, rgba > 0xffffff);

    Dimension size = new Dimension(myWidth, myHeight);
    setAutoRequestFocus(false);
    setSize(size);
    setLocationInTheCenterOfScreen();
  }

  private static @Nullable Icon getProgressTailIcon(@NotNull ApplicationInfoEx info) {
    String progressTailIconName = info.getProgressTailIcon();
    Icon progressTail = null;
    if (progressTailIconName != null) {
      try {
        Image image = ImageLoader.loadFromUrl(Splash.class.getResource(progressTailIconName));
        if (image != null) {
          progressTail = new JBImageIcon(image);
        }
      }
      catch (Exception ignore) {
      }
    }
    return progressTail;
  }

  public void initAndShow(Boolean visible) {
    if (myProgressSlidePainter != null) {
      myProgressSlidePainter.startPreloading();
    }
    StartUpMeasurer.addInstantEvent("splash shown");
    Activity activity = StartUpMeasurer.startActivity("splash set visible");
    setVisible(visible);
    activity.end();
    if (visible) {
      paint(getGraphics());
      toFront();
    }
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @NotNull
  private static Image loadImage(@NotNull String path) {
    Image result = SplashSlideLoader.loadImage(path);
    if (result == null) {
      throw new IllegalStateException("Cannot find image: " + path);
    }
    return result;
  }

  @Override
  public void paint(Graphics g) {
    if (myProgress < 0.10 || myProgressSlidePainter == null) {
      StartupUiUtil.drawImage(g, myImage, 0, 0, null);
    }
    else {
      paintProgress(g);
    }
  }

  private void setLocationInTheCenterOfScreen() {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    if (SystemInfo.isWindows) {
      JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(getGraphicsConfiguration()));
    }
    setLocation(StartupUiUtil.getCenterPoint(bounds, getSize()));
  }

  public void showProgress(double progress) {
    if (myProgressColor == null) {
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
    if (g == null) {
      return;
    }

    if (myProgressSlidePainter != null) {
      myProgressSlidePainter.paintSlides(g, myProgress);
    }

    Color progressColor = myProgressColor;
    if (progressColor == null) {
      return;
    }

    int progressWidth = (int)(myWidth * myProgress);
    int currentWidth = progressWidth - myProgressLastPosition;
    if (currentWidth == 0) {
      return;
    }

    g.setColor(progressColor);
    int y = myProgressSlidePainter == null ? myProgressY : myHeight - myProgressHeight;
    g.fillRect(myProgressLastPosition, y, currentWidth, myProgressHeight);
    if (myProgressTail != null) {
      int tx = (int)(currentWidth - (myProgressTail.getIconWidth() / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE));
      int ty = (int)(myProgressY - (myProgressTail.getIconHeight() - myProgressHeight) / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE);
      myProgressTail.paintIcon(this, g, tx, ty);
    }
    myProgressLastPosition = progressWidth;
  }

  private static int uiScale(int i) {
    return (int)(i * JBUI_INIT_SCALE);
  }
}
