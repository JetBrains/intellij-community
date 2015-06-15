/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.printer.idea;

import com.intellij.vcs.log.graph.SimplePrintElement;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.vcs.log.printer.idea.PrintParameters.*;

public class PositionUtil {
  private static float distance(int x1, int y1, int x2, int y2) {
    return (float)Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
  }

  public static boolean overUpEdge(int upPosition, int downPosition, int x, int y, int rowHeight) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * downPosition + WIDTH_NODE / 2;
    int y1 = rowHeight / 2;
    int x2 = WIDTH_NODE * upPosition + WIDTH_NODE / 2;
    int y2 = -rowHeight / 2;
    //return true;
    return (distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick);
  }

  public static boolean overDownEdge(int upPosition, int downPosition, int x, int y, int rowHeight) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * upPosition + WIDTH_NODE / 2;
    int y1 = rowHeight / 2;
    int x2 = WIDTH_NODE * downPosition + WIDTH_NODE / 2;
    int y2 = rowHeight + rowHeight / 2;
    return distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick;
  }

  public static boolean overNode(int position, int x, int y, SimplePrintElement.Type type, int rowHeight) {
    int r = CIRCLE_RADIUS;
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = rowHeight / 2;
    if (type == SimplePrintElement.Type.DOWN_ARROW) y0 = rowHeight - r;
    if (type == SimplePrintElement.Type.UP_ARROW) y0 = r;

    return distance(x0, y0, x, y) <= r;
  }

  public static int getYInsideRow(@NotNull Point point, int rowHeight) {
    return point.y - getRowIndex(point, rowHeight) * rowHeight;
  }

  public static int getRowIndex(@NotNull Point point, int rowHeight) {
    return point.y / rowHeight;
  }
}
