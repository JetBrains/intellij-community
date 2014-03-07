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
package com.intellij.vcs.log.newgraph.render;

import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.ShortEdge;
import com.intellij.vcs.log.newgraph.render.cell.SpecialRowElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.vcs.log.graph.render.PrintParameters.*;

public class PositionUtil {
  private static float distance(int x1, int y1, int x2, int y2) {
    return (float)Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
  }

  public static boolean overUpEdge(ShortEdge edge, int x, int y) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2;
    int y2 = -HEIGHT_CELL / 2;
    //return true;
    return (distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick);
  }

  public static boolean overDownEdge(ShortEdge edge, int x, int y) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2;
    int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
    return distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick;
  }

  public static boolean overNode(int position, int x, int y) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    return distance(x0, y0, x, y) <= r;
  }

  public static int getYInsideRow(@NotNull Point point) {
    return point.y - getRowIndex(point) * HEIGHT_CELL;
  }

  public static int getRowIndex(@NotNull Point point) {
    return point.y / HEIGHT_CELL;
  }

  @Nullable
  public static Node getNode(@Nullable GraphCell cell) {
    if (cell == null) {
      return null;
    }
    for (SpecialRowElement element : cell.getSpecialRowElements()) {
      GraphElement graphElement = element.getElement();
      if (graphElement instanceof Node) {
        return (Node) graphElement;
      }
    }
    return null;
  }
}
