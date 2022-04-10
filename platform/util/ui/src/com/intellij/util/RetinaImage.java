// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.ScaleContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public final class RetinaImage { // [tav] todo: create HiDPIImage class
  /**
   * Creates a Retina-aware wrapper over a raw image.
   * The raw image should be provided in scale of the Retina default scale factor (2x).
   * The wrapper will represent the raw image in the user coordinate space.
   *
   * @param image the raw image
   * @return the Retina-aware wrapper
   */
  public static Image createFrom(@NotNull Image image) {
    int w = image.getWidth(ImageLoader.ourComponent);
    int h = image.getHeight(ImageLoader.ourComponent);
    return new JBHiDPIScaledImage(image, w / (double)2, h / (double)2, BufferedImage.TYPE_INT_ARGB);
  }

  /**
   * @deprecated use {@link #createFrom(Image, double, ImageObserver)} instead
   */
  @Deprecated(forRemoval = true)
  public static @NotNull Image createFrom(@NotNull Image image, int scale, ImageObserver observer) {
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
  public static @NotNull Image createFrom(@NotNull Image image, double scale, ImageObserver observer) {
    int w = image.getWidth(observer);
    int h = image.getHeight(observer);
    return new JBHiDPIScaledImage(image, w / scale, h / scale, BufferedImage.TYPE_INT_ARGB);
  }

  public static @NotNull BufferedImage create(int width, int height, int type) {
    return new JBHiDPIScaledImage(width, height, type);
  }

  public static @NotNull BufferedImage create(Graphics2D g, int width, int height, int type) {
    return new JBHiDPIScaledImage(g, width, height, type);
  }

  public static @NotNull BufferedImage create(Graphics2D g, double width, double height, int type, @NotNull RoundingMode rm) {
    return new JBHiDPIScaledImage(g, width, height, type, rm);
  }

  public static @NotNull BufferedImage create(GraphicsConfiguration gc, int width, int height, int type) {
    return new JBHiDPIScaledImage(gc, width, height, type);
  }

  public static @NotNull BufferedImage create(GraphicsConfiguration gc, double width, double height, int type, @NotNull RoundingMode rm) {
    return new JBHiDPIScaledImage(gc, width, height, type, rm);
  }

  public static @NotNull BufferedImage create(ScaleContext ctx, double width, double height, int type, @NotNull RoundingMode rm) {
    return new JBHiDPIScaledImage(ctx, width, height, type, rm);
  }
}
