// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class RetinaImage { // [tav] todo: create HiDPIImage class

  /**
   * Creates a Retina-aware wrapper over a raw image.
   * The raw image should be provided in scale of the Retina default scale factor (2x).
   * The wrapper will represent the raw image in the user coordinate space.
   *
   * @param image the raw image
   * @return the Retina-aware wrapper
   */
  public static Image createFrom(Image image) {
    return createFrom(image, 2, ImageLoader.ourComponent);
  }

  /**
   * @deprecated use {@link #createFrom(Image, float, ImageObserver)} instead
   */
  @Deprecated
  @NotNull
  public static Image createFrom(Image image, int scale, ImageObserver observer) {
    return createFrom(image, (float)scale, observer);
  }

  /**
   * Creates a Retina-aware wrapper over a raw image.
   * The raw image should be provided in the specified scale.
   * The wrapper will represent the raw image in the user coordinate space.
   *
   * @param image the raw image
   * @param scale the raw image scale
   * @param observer the raw image observer
   * @return the Retina-aware wrapper
   */
  @NotNull
  public static Image createFrom(Image image, double scale, ImageObserver observer) {
    int w = image.getWidth(observer);
    int h = image.getHeight(observer);
    return new JBHiDPIScaledImage(image, w / scale, h / scale, BufferedImage.TYPE_INT_ARGB);
  }

  @NotNull
  public static BufferedImage create(int width, int height, int type) {
    return new JBHiDPIScaledImage(width, height, type);
  }

  @NotNull
  public static BufferedImage create(Graphics2D g, int width, int height, int type) {
    return new JBHiDPIScaledImage(g, width, height, type);
  }

  @NotNull
  public static BufferedImage create(Graphics2D g, double width, double height, int type, RoundingMode rm) {
    return new JBHiDPIScaledImage(g, width, height, type, rm);
  }

  @NotNull
  public static BufferedImage create(GraphicsConfiguration gc, int width, int height, int type) {
    return new JBHiDPIScaledImage(gc, width, height, type);
  }

  @NotNull
  public static BufferedImage create(GraphicsConfiguration gc, double width, double height, int type, RoundingMode rm) {
    return new JBHiDPIScaledImage(gc, width, height, type, rm);
  }

  @NotNull
  public static BufferedImage create(ScaleContext ctx, double width, double height, int type, RoundingMode rm) {
    return new JBHiDPIScaledImage(ctx, width, height, type, rm);
  }

  /** @deprecated Apple JRE is no longer supported (to be removed in IDEA 2019) */
  @Deprecated
  public static boolean isAppleHiDPIScaledImage(@SuppressWarnings("unused") Image image) {
    return false;
  }
}
