// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ImageUtil;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import static java.lang.Math.round;

/**
* @author Konstantin Bulenkov
* @author tav
*/
public final class JBHiDPIScaledImage extends BufferedImage {
  @Nullable private final Image myImage;
  private final double myUserWidth;
  private final double myUserHeight;
  private final double myScale;

  /**
   * @see #JBHiDPIScaledImage(double, double, int)
   */
  public JBHiDPIScaledImage(int width, int height, int type) {
    this((double)width, (double)height, type);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the system default scale.
   *
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(double width, double height, int type) {
    this((GraphicsConfiguration)null, width, height, type);
  }

  /**
   * @see #JBHiDPIScaledImage(Graphics2D, double, double, int, RoundingMode rm)
   */
  public JBHiDPIScaledImage(@Nullable Graphics2D g, int width, int height, int type) {
    this(g, (double)width, (double)height, type, RoundingMode.FLOOR);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics scale.
   *
   * @param g the graphics which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   * @param rm the rounding mode
   */
  public JBHiDPIScaledImage(@Nullable Graphics2D g, double width, double height, int type, @NotNull RoundingMode rm) {
    this(JBUIScale.sysScale(g), width, height, type, rm);
  }

  /**
   * @see #JBHiDPIScaledImage(GraphicsConfiguration, double, double, int)
   */
  public JBHiDPIScaledImage(@Nullable GraphicsConfiguration gc, int width, int height, int type) {
    this(gc, (double)width, (double)height, type);
  }

  /**
   * @see #JBHiDPIScaledImage(GraphicsConfiguration, double, double, int)
   */
  public JBHiDPIScaledImage(@Nullable ScaleContext ctx, double width, double height, int type, @NotNull RoundingMode rm) {
    this(JBUIScale.sysScale(ctx), width, height, type, rm);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics config.
   *
   * @param gc the graphics config which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(@Nullable GraphicsConfiguration gc, double width, double height, int type) {
    this(gc, width, height, type, RoundingMode.FLOOR);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics config.
   *
   * @param gc the graphics config which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param rm the rounding mode to apply when converting width/height to the device space
   * @param type the type
   */
  public JBHiDPIScaledImage(@Nullable GraphicsConfiguration gc, double width, double height, int type, @NotNull RoundingMode rm) {
    this(JBUIScale.sysScale(gc), width, height, type, rm);
  }

  private JBHiDPIScaledImage(double scale, double width, double height, int type, @NotNull RoundingMode rm) {
    super(rm.round(width * scale), rm.round(height * scale), type);
    myImage = null;
    myUserWidth = width;
    myUserHeight = height;
    myScale = scale;
  }

  /**
   * @see #JBHiDPIScaledImage(Image, double, double, int)
   */
  public JBHiDPIScaledImage(@NotNull Image image, int width, int height, int type) {
    this(image, (double)width, (double)height, type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage wrapper for the provided scaled raw image.
   * The wrapper image will represent the scaled raw image in user coordinate space.
   *
   * @param image the scaled raw image
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(@NotNull Image image, double width, double height, int type) {
    super(1, 1, type); // a dummy wrapper
    myImage = image;
    myUserWidth = width;
    myUserHeight = height;
    myScale = myUserWidth > 0 ? myImage.getWidth(null) / myUserWidth : 1f;
  }

  public double getScale() {
    return myScale;
  }

  /**
   * Returns JBHiDPIScaledImage of the same structure scaled by the provided factor.
   *
   * @param scaleFactor the scale factor
   * @return scaled instance
   */
  @NotNull
  public JBHiDPIScaledImage scale(double scaleFactor) {
    Image img = myImage == null ? this: myImage;

    int w = (int)(scaleFactor * getRealWidth(null));
    int h = (int)(scaleFactor * getRealHeight(null));
    if (w <= 0 || h <= 0) return this;

    Image scaled = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, w, h);

    double newUserWidth = w / myScale;
    double newUserHeight = h / myScale;

    if (myImage != null) {
      return new JBHiDPIScaledImage(scaled, newUserWidth, newUserHeight, getType());
    }
    JBHiDPIScaledImage newImg = new JBHiDPIScaledImage(myScale, newUserWidth, newUserHeight, getType(), RoundingMode.ROUND);
    Graphics2D g = newImg.createGraphics();
    g.drawImage(scaled, 0, 0, (int)round(newUserWidth), (int)round(newUserHeight),
                0, 0, scaled.getWidth(null), scaled.getHeight(null), null);
    g.dispose();
    return newImg;
  }

  /**
   * Returns JBHiDPIScaledImage of the same structure scaled to the provided dimensions.
   * Dimensions are in user-space coordinates (unscaled)
   *
   * @return scaled instance
   */
  @NotNull
  public JBHiDPIScaledImage scale(int targetUserWidth, int targetUserHeight) {
    Image img = myImage == null ? this : myImage;

    int w = getUserWidth(null);
    int h = getUserHeight(null);
    if (w <= 0 || h <= 0 || w == targetUserWidth && h == targetUserHeight) return this;

    int targetWidth = (int)round(targetUserWidth * myScale);
    int targetHeight = (int)round(targetUserHeight * myScale);

    Image scaled = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight);

    if (myImage != null) {
      return new JBHiDPIScaledImage(scaled, targetUserWidth, targetUserHeight, getType());
    }
    JBHiDPIScaledImage newImg = new JBHiDPIScaledImage(myScale, targetUserWidth, targetUserHeight, getType(), RoundingMode.ROUND);
    Graphics2D g = newImg.createGraphics();
    g.drawImage(scaled, 0, 0, targetUserWidth, targetUserHeight,
                0, 0, scaled.getWidth(null), scaled.getHeight(null), null);
    g.dispose();
    return newImg;
  }

  public Image getDelegate() {
    return myImage;
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  @Override
  public int getWidth() {
    return getWidth(null);
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  @Override
  public int getHeight() {
    return getHeight(null);
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  @Override
  public int getWidth(ImageObserver observer) {
    return myImage != null ? getUserWidth(observer) : getRealWidth(observer);
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  @Override
  public int getHeight(ImageObserver observer) {
    return myImage != null ? getUserHeight(observer) : getRealHeight(observer);
  }

  /**
   * Returns the width in user coordinate space.
   *
   * @param observer the image observer
   * @return the width
   */
  public int getUserWidth(ImageObserver observer) {
    return myImage != null ? (int)round(myUserWidth) : (int)round(super.getWidth(observer) / myScale);
  }

  /**
   * Returns the height in user coordinate space.
   *
   * @param observer the image observer
   * @return the height
   */
  public int getUserHeight(ImageObserver observer) {
    return myImage != null ? (int)round(myUserHeight) : (int)round(super.getHeight(observer) / myScale);
  }

  /**
   * Returns the real width.
   *
   * @param observer the image observer
   * @return the width
   */
  public int getRealWidth(ImageObserver observer) {
    return myImage != null ? myImage.getWidth(observer) : super.getWidth(observer);
  }

  /**
   * Returns the real height.
   *
   * @param observer the image observer
   * @return the height
   */
  public int getRealHeight(ImageObserver observer) {
    return myImage != null ? myImage.getHeight(observer) : super.getHeight(observer);
  }

  @Override
  public Graphics2D createGraphics() {
    Graphics2D g = super.createGraphics();
    if (myImage == null) {
      g.scale(myScale, myScale);
      return new HiDPIScaledGraphics(g);
    }
    return g;
  }
}
