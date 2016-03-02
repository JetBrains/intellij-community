/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.paint;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class PositionUtil {
  private static float distance(int x1, int y1, int x2, int y2) {
    return (float)Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
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
