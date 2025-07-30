// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PaintParameters {

  private static final int WIDTH_NODE = 20;
  public static final int CIRCLE_RADIUS = 4;
  private static final double THICK_LINE = 1.5;
  private static final double SELECT_THICK_LINE = 2.5;

  public static final int ROW_HEIGHT = 22;

  public static double getElementWidth(int rowHeight) {
    return scaleWithRowHeight(WIDTH_NODE, rowHeight);
  }

  public static double getLineThickness(int rowHeight) {
    return scaleWithRowHeight(THICK_LINE, rowHeight);
  }

  public static double getSelectedLineThickness(int rowHeight) {
    return scaleWithRowHeight(SELECT_THICK_LINE, rowHeight);
  }

  public static double getCircleRadius(int rowHeight) {
    return scaleWithRowHeight(CIRCLE_RADIUS, rowHeight);
  }

  public static double scaleWithRowHeight(double value, int actualHeight) {
    return (value * actualHeight) / ROW_HEIGHT;
  }

  public static double scaleWithRowHeight(int value, int actualHeight) {
    return scaleWithRowHeight((double)value, actualHeight);
  }
}
