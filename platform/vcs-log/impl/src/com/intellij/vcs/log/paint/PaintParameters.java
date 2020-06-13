// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.paint;

public final class PaintParameters {

  private static final int WIDTH_NODE = 15;
  private static final int CIRCLE_RADIUS = 4;
  private static final int SELECT_CIRCLE_RADIUS = 5;
  private static final float THICK_LINE = 1.5f;
  private static final float SELECT_THICK_LINE = 2.5f;

  public static final int ROW_HEIGHT = 22;

  public static int getNodeWidth(int rowHeight) {
    return WIDTH_NODE * rowHeight / ROW_HEIGHT;
  }

  public static float getLineThickness(int rowHeight) {
    return THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static float getSelectedLineThickness(int rowHeight) {
    return SELECT_THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static int getSelectedCircleRadius(int rowHeight) {
    return SELECT_CIRCLE_RADIUS * rowHeight / ROW_HEIGHT;
  }

  public static int getCircleRadius(int rowHeight) {
    return CIRCLE_RADIUS * rowHeight / ROW_HEIGHT;
  }
}
