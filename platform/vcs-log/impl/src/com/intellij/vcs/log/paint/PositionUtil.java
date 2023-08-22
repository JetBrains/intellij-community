// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.paint;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class PositionUtil {
  private static float distance(int x1, int y1, int x2, int y2) {
    return (float)Math.hypot(x1 - x2, y1 - y2);
  }

  public static boolean overUpEdge(int upPosition, int downPosition, int x, int y, int rowHeight, int nodeWidth, float lineThickness) {
    int x1 = nodeWidth * downPosition + nodeWidth / 2;
    int y1 = rowHeight / 2;
    int x2 = nodeWidth * upPosition + nodeWidth / 2;
    int y2 = -rowHeight / 2;
    //return true;
    return (distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + lineThickness);
  }

  public static boolean overDownEdge(int upPosition, int downPosition, int x, int y, int rowHeight, int nodeWidth, float lineThickness) {
    int x1 = nodeWidth * upPosition + nodeWidth / 2;
    int y1 = rowHeight / 2;
    int x2 = nodeWidth * downPosition + nodeWidth / 2;
    int y2 = rowHeight + rowHeight / 2;
    return distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + lineThickness;
  }

  public static boolean overNode(int position, int x, int y, int rowHeight, int nodeWidth, int circleRadius) {
    int x0 = nodeWidth * position + nodeWidth / 2;
    int y0 = rowHeight / 2;

    return distance(x0, y0, x, y) <= circleRadius;
  }

  public static int getYInsideRow(@NotNull Point point, int rowHeight) {
    return point.y - getRowIndex(point, rowHeight) * rowHeight;
  }

  public static int getRowIndex(@NotNull Point point, int rowHeight) {
    return point.y / rowHeight;
  }
}
