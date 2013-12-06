package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.printmodel.ShortEdge;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

import static com.intellij.vcs.log.graph.render.PrintParameters.*;

public class PositionUtil {
  private static float distance(int x1, int y1, int x2, int y2) {
    return (float)Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
  }

  public static boolean overUpEdge(ShortEdge edge, int x, int y, int offset) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2 + offset;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2 + offset;
    int y2 = -HEIGHT_CELL / 2;
    //return true;
    return (distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick);
  }

  public static boolean overDownEdge(ShortEdge edge, int x, int y, int offset) {
    float thick = THICK_LINE;
    int x1 = WIDTH_NODE * edge.getUpPosition() + WIDTH_NODE / 2 + offset;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * edge.getDownPosition() + WIDTH_NODE / 2 + offset;
    int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
    return distance(x1, y1, x, y) + distance(x2, y2, x, y) < distance(x1, y1, x2, y2) + thick;
  }

  public static boolean overNode(int position, int x, int y, int xOffset) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2 + xOffset;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    return distance(x0, y0, x, y) <= r;
  }

  public static int getYInsideRow(MouseEvent e) {
    return e.getY() - getRowIndex(e) * HEIGHT_CELL;
  }

  public static int getRowIndex(MouseEvent e) {
    return e.getY() / HEIGHT_CELL;
  }

  @Nullable
  public static Node getNode(@Nullable GraphPrintCell cell) {
    if (cell == null) {
      return null;
    }
    for (SpecialPrintElement element : cell.getSpecialPrintElements()) {
      Node node = element.getGraphElement().getNode();
      if (node != null) {
        return node;
      }
    }
    return null;
  }
}
