// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * A {@code double} version of RegionPainter.
 *
 * @author tav
 * @see RegionPainter
 */
public interface RegionPainter2D<T> {
  /**
   * Paints on the given {@link Graphics2D} object.
   *
   * @param g the {@code Graphics2D} object to render to
   * @param x X position of the area to paint
   * @param y Y position of the area to paint
   * @param width width of the area to paint
   * @param height height of the area to paint
   * @param object an optional configuration parameter
   * @param strokeType the stroke type
   * @param strokeWidth the stroke width
   * @param valueAA overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA}
   */
  void paint(@NotNull Graphics2D g,
             double x, double y, double width, double height,
             @Nullable T object,
             @NotNull StrokeType strokeType,
             double strokeWidth,
             @NotNull Object valueAA);

  void paint(@NotNull Graphics2D g,
             double x, double y, double width, double height,
             @NotNull StrokeType strokeType,
             double strokeWidth);

  void paint(@NotNull Graphics2D g, double x, double y, double width, double height);

  void paint(@NotNull final Graphics2D g,
             Rectangle2D rect,
             @Nullable T object,
             @NotNull StrokeType strokeType,
             double strokeWidth,
             @NotNull Object valueAA);
}
