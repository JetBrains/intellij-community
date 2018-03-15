// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

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
   */
  void paint(Graphics2D g, double x, double y, double width, double height, @Nullable T object);
}
