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

import com.intellij.ui.JBColor;
import com.intellij.vcs.log.printer.idea.PrintParameters;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.ThickHoverController;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.ShortEdge;
import com.intellij.vcs.log.newgraph.render.cell.SpecialRowElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {

  private static final Color MARK_COLOR = JBColor.BLACK;

  private final Stroke usual = new BasicStroke(PrintParameters.THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke hide = new BasicStroke(PrintParameters.THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
  private final Stroke selectUsual = new BasicStroke(PrintParameters.SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke selectHide = new BasicStroke(PrintParameters.SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);

  private Graphics2D g2;

  @NotNull
  private final ThickHoverController myThickHoverController;

  @NotNull
  private final ElementColorManager myColorManager;

  public SimpleGraphCellPainter(@NotNull ThickHoverController thickHoverController, @NotNull ElementColorManager colorManager) {
    myThickHoverController = thickHoverController;
    myColorManager = colorManager;
  }

  private void paintUpLine(int from, int to, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
    int y1 = PrintParameters.HEIGHT_CELL / 2;
    int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
    int y2 = -PrintParameters.HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x2, y2, x1, y1);
  }

  private void paintDownLine(int from, int to, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
    int y1 = PrintParameters.HEIGHT_CELL / 2;
    int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
    int y2 = PrintParameters.HEIGHT_CELL + PrintParameters.HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x1, y1, x2, y2);
  }

  private void paintAbove(int position, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * position + 3;
    int y = 4;
    int x2 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE - 4;
    g2.setColor(color);
    g2.drawLine(x1, y, x2, y);
  }

  private void paintBelow(int position, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * position + 3;
    int y = PrintParameters.HEIGHT_CELL - 4;
    int x2 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE - 4;
    g2.setColor(color);
    g2.drawLine(x1, y, x2, y);
  }


  private void paintCircle(int position, Color color, boolean select) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int y0 = PrintParameters.HEIGHT_CELL / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    if (select) {
      r = PrintParameters.SELECT_CIRCLE_RADIUS;
    }
    Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
    g2.setColor(color);
    g2.fill(circle);
  }

  private void paintHide(int position, Color color) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int y0 = PrintParameters.HEIGHT_CELL / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 + r);
    g2.drawLine(x0, y0 + r, x0 + r, y0);
    g2.drawLine(x0, y0 + r, x0 - r, y0);
  }

  private void paintShow(int position, Color color) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int y0 = PrintParameters.HEIGHT_CELL / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 - r);
    g2.drawLine(x0, y0 - r, x0 + r, y0);
    g2.drawLine(x0, y0 - r, x0 - r, y0);
  }

  private void paintDownHarmonica(Color color) {
    int x0 = 0;
    int y0 = PrintParameters.HEIGHT_CELL - 1;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0 + PrintParameters.WIDTH_NODE / 2 , y0);
  }

  private void setStroke(boolean usual, boolean select) {
    if (usual) {
      if (select) {
        g2.setStroke(selectUsual);
      }
      else {
        g2.setStroke(this.usual);
      }
    }
    else {
      if (select) {
        g2.setStroke(selectHide);
      }
      else {
        g2.setStroke(hide);
      }
    }
  }

  private interface LitePrinter {
    void print(Color color);
  }

  private void drawLogic(boolean selected, boolean marked, boolean isUsual, Color usualColor, LitePrinter printer) {
    if (marked) {
      setStroke(isUsual, true);
      printer.print(MARK_COLOR);
      setStroke(isUsual, false);
      printer.print(usualColor);
    } else {
      setStroke(isUsual, selected);
      printer.print(usualColor);
    }
  }


  private boolean isSelected(@NotNull GraphElement graphElement) {
    return myThickHoverController.isThick(graphElement);
  }

  private boolean isMarked(@NotNull GraphElement graphElement) {
    return myThickHoverController.isHover(graphElement);
  }

  private static boolean isUsual(@NotNull Edge edge) {
    return edge.getType() == Edge.Type.USUAL;
  }

  @NotNull
  private JBColor getColor(@NotNull GraphElement element) {
    return myColorManager.getColor(element);
  }

  private boolean isMarked(@NotNull SpecialRowElement specialRowElement) {
    return myThickHoverController.isHover(specialRowElement) || myThickHoverController.isHover(specialRowElement.getElement());
  }

  @Override
  public void draw(@NotNull Graphics2D g2, @NotNull GraphCell row) {
    this.g2 = g2;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (final ShortEdge shortEdge : row.getUpEdges()) {
      Edge edge = shortEdge.getEdge();
      drawLogic(isSelected(edge), isMarked(edge), isUsual(edge), getColor(edge), new LitePrinter() {
        @Override
        public void print(Color color) {
          paintUpLine(shortEdge.getDownPosition(), shortEdge.getUpPosition(), color);
        }
      });
    }
    for (final ShortEdge shortEdge : row.getDownEdges()) {
      Edge edge = shortEdge.getEdge();
      drawLogic(isSelected(edge), isMarked(edge), isUsual(edge), getColor(edge), new LitePrinter() {
        @Override
        public void print(Color color) {
          paintDownLine(shortEdge.getUpPosition(), shortEdge.getDownPosition(), color);
        }
      });

    }
    for (final SpecialRowElement rowElement : row.getSpecialRowElements()) {
      GraphElement element = rowElement.getElement();
      final Edge edge;
      switch (rowElement.getType()) {
        case NODE:
          assert element instanceof Node;
          Node node = (Node) element;
          if (isMarked(node)) {
            paintCircle(rowElement.getPosition(), MARK_COLOR, true);
            paintCircle(rowElement.getPosition(), getColor(node), false);
          } else {
            paintCircle(rowElement.getPosition(), getColor(node), isSelected(node));
          }
          break;
        case UP_ARROW:
          assert element instanceof Edge;
          edge = (Edge) element;
          drawLogic(isSelected(edge), isMarked(rowElement), isUsual(edge), getColor(edge), new LitePrinter() {
            @Override
            public void print(Color color) {
              paintShow(rowElement.getPosition(), color);
            }
          });
          break;
        case DOWN_ARROW:
          assert element instanceof Edge;
          edge = (Edge) element;
          drawLogic(isSelected(edge), isMarked(rowElement), isUsual(edge), getColor(edge), new LitePrinter() {
            @Override
            public void print(Color color) {
              paintHide(rowElement.getPosition(), color);
            }
          });
          break;
        case DOWN_HARMONICA:
          drawLogic(true, false, true, JBColor.BLACK, new LitePrinter() {
            @Override
            public void print(Color color) {
              //paintDownHarmonica(color);
            }
          });
          break;
        default:
          throw new IllegalStateException();
      }
    }

  }

  @Nullable
  @Override
  public GraphElement mouseOver(@NotNull GraphCell graphCell, int x, int y) {
    for (SpecialRowElement printElement : graphCell.getSpecialRowElements()) {
      if (printElement.getType() == SpecialRowElement.Type.NODE) {
        if (PositionUtil.overNode(printElement.getPosition(), x, y)) {
          return printElement.getElement();
        }
      }
    }
    for (ShortEdge edge : graphCell.getUpEdges()) {
      if (PositionUtil.overUpEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }
    for (ShortEdge edge : graphCell.getDownEdges()) {
      if (PositionUtil.overDownEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }

    return null;
  }

  @Nullable
  @Override
  public SpecialRowElement mouseOverArrow(@NotNull GraphCell graphCell, int x, int y) {
    for (SpecialRowElement rowElement : graphCell.getSpecialRowElements()) {
      if (rowElement.getType() == SpecialRowElement.Type.UP_ARROW || rowElement.getType() == SpecialRowElement.Type.DOWN_ARROW) {
        if (PositionUtil.overNode(rowElement.getPosition(), x, y)) {
          return rowElement;
        }
      }
    }
    return null;
  }

}
