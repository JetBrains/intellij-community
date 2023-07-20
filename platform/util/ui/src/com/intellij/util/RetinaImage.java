// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.ui.icons.HiDPIImage;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public final class RetinaImage {
  private static final SynchronizedClearableLazy<Component> component = new SynchronizedClearableLazy<>(() -> new Component() {
  });

  /**
   * Creates a Retina-aware wrapper over a raw image.
   * The raw image should be provided on the scale of the Retina default scale factor (2x).
   * The wrapper will represent the raw image in the user coordinate space.
   *
   * @param image the raw image
   * @return the Retina-aware wrapper
   */
  public static Image createFrom(@NotNull Image image) {
    Component component = RetinaImage.component.get();
    int w = image.getWidth(component);
    int h = image.getHeight(component);
    return new HiDPIImage(image, w / (double)2, h / (double)2, BufferedImage.TYPE_INT_ARGB);
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
   * The raw image should be provided on the specified scale.
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
    return new HiDPIImage(image, w / scale, h / scale, BufferedImage.TYPE_INT_ARGB);
  }

  public static @NotNull BufferedImage create(GraphicsConfiguration gc, double width, double height, int type, @NotNull RoundingMode rm) {
    return new HiDPIImage(gc, width, height, type, rm);
  }
}
