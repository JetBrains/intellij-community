// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PaintParameters {

  private static final int WIDTH_NODE = 20;
  private static final int CIRCLE_RADIUS = 4;
  private static final double THICK_LINE = 1.5;
  private static final double SELECT_THICK_LINE = 2.5;

  public static final int ROW_HEIGHT = 22;

  public static double getElementWidth(int rowHeight) {
    return (double)(WIDTH_NODE * rowHeight) / ROW_HEIGHT;
  }

  public static double getLineThickness(int rowHeight) {
    return THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static double getSelectedLineThickness(int rowHeight) {
    return SELECT_THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static double getCircleRadius(int rowHeight) {
    return getCircleRadius(CIRCLE_RADIUS, rowHeight);
  }

  public static double getCircleRadius(int radius, int rowHeight) {
    return (double)(radius * rowHeight) / ROW_HEIGHT;
  }
}
