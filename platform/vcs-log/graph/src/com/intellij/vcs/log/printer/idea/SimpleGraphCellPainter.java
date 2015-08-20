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

import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Collection;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {

  private static final Color MARK_COLOR = JBColor.BLACK;
  private static final int ROW_HEIGHT = 24;
  private static final double ARROW_ANGLE_COS2 = 0.7;
  private static final double ARROW_LENGTH = 0.3;

  private final Stroke usual = new BasicStroke(PrintParameters.THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke hide =
    new BasicStroke(PrintParameters.THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
  private final Stroke selectUsual = new BasicStroke(PrintParameters.SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke selectHide =
    new BasicStroke(PrintParameters.SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);

  private Graphics2D g2;

  @NotNull private final ColorGenerator myColorGenerator;

  public SimpleGraphCellPainter(@NotNull ColorGenerator colorGenerator) {
    myColorGenerator = colorGenerator;
  }

  protected int getRowHeight() {
    return ROW_HEIGHT;
  }

  private void paintUpLine(int from, int to, Color color, boolean hasArrow) {
    // paint vertical lines normal size
    // paint non-vertical lines twice the size to make them dock with each other well
    if (from == to) {
      int x = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
      int y1 = getRowHeight() / 2 - 1;
      int y2 = 0;
      paintLine(color, hasArrow, x, y1, x, y2, x, y2);
    }
    else {
      int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
      int y1 = getRowHeight() / 2;
      int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
      int y2 = -getRowHeight() / 2;
      paintLine(color, hasArrow, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2);
    }
  }

  private void paintDownLine(int from, int to, Color color, boolean hasArrow) {
    if (from == to) {
      int y2 = getRowHeight() - 1;
      int y1 = getRowHeight() / 2;
      int x = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
      paintLine(color, hasArrow, x, y1, x, y2, x, y2);
    }
    else {
      int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
      int y1 = getRowHeight() / 2;
      int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
      int y2 = getRowHeight() + getRowHeight() / 2;
      paintLine(color, hasArrow, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2);
    }
  }

  private void paintLine(Color color, boolean hasArrow, int x1, int y1, int x2, int y2, int startArrowX, int startArrowY) {
    g2.setColor(color);
    g2.drawLine(x1, y1, x2, y2);
    if (hasArrow) {
      Pair<Integer, Integer> rotate1 =
        rotate(x1, y1, startArrowX, startArrowY, Math.sqrt(ARROW_ANGLE_COS2), Math.sqrt(1 - ARROW_ANGLE_COS2), ARROW_LENGTH * getRowHeight());
      Pair<Integer, Integer> rotate2 =
        rotate(x1, y1, startArrowX, startArrowY, Math.sqrt(ARROW_ANGLE_COS2), -Math.sqrt(1 - ARROW_ANGLE_COS2), ARROW_LENGTH * getRowHeight());
      g2.drawLine(startArrowX, startArrowY, rotate1.first, rotate1.second);
      g2.drawLine(startArrowX, startArrowY, rotate2.first, rotate2.second);
    }
  }

  @NotNull
  private static Pair<Integer, Integer> rotate(double x, double y, double centerX, double centerY, double cos, double sin, double arrowLength) {
    double translateX = (x - centerX);
    double translateY = (y - centerY);

    double d = Math.sqrt(translateX * translateX + translateY * translateY);
    double scaleX = arrowLength * translateX / d;
    double scaleY = arrowLength * translateY / d;

    double rotateX = scaleX * cos - scaleY * sin;
    double rotateY = scaleX * sin + scaleY * cos;

    return Pair.create((int)Math.round(rotateX + centerX), (int)Math.round(rotateY + centerY));
  }

  private void paintCircle(int position, Color color, boolean select) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int y0 = getRowHeight() / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    if (select) {
      r = PrintParameters.SELECT_CIRCLE_RADIUS;
    }
    Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
    g2.setColor(color);
    g2.fill(circle);
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

  private void drawLogic(boolean isSelected, boolean isUsual, Color usualColor, LitePrinter printer) {
    if (isSelected) {
      setStroke(isUsual, true);
      printer.print(MARK_COLOR);
      setStroke(isUsual, false);
      printer.print(usualColor);
    }
    else {
      setStroke(isUsual, false);
      printer.print(usualColor);
    }
  }

  @NotNull
  private Color getColor(@NotNull PrintElement printElement) {
    return myColorGenerator.getColor(printElement.getColorId());
  }

  private static boolean isUsual(PrintElement printElement) {
    if (!(printElement instanceof EdgePrintElement)) return true;
    EdgePrintElement.LineStyle lineStyle = ((EdgePrintElement)printElement).getLineStyle();
    return lineStyle == EdgePrintElement.LineStyle.SOLID;
  }

  @Override
  public void draw(@NotNull Graphics2D g2, @NotNull Collection<? extends PrintElement> printElements) {
    this.g2 = g2;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    for (final PrintElement printElement : printElements) {
      LitePrinter printer = null;
      if (printElement instanceof EdgePrintElement) {
        printer = new LitePrinter() {
          @Override
          public void print(Color color) {
            EdgePrintElement edgePrintElement = (EdgePrintElement)printElement;
            int from = edgePrintElement.getPositionInCurrentRow();
            int to = edgePrintElement.getPositionInOtherRow();

            if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
              paintDownLine(from, to, color, edgePrintElement.hasArrow());
            }
            else {
              paintUpLine(from, to, color, edgePrintElement.hasArrow());
            }
          }
        };
      }

      if (printElement instanceof NodePrintElement) {
        int position = printElement.getPositionInCurrentRow();
        if (printElement.isSelected()) {
          paintCircle(position, MARK_COLOR, true);
          paintCircle(position, getColor(printElement), false);
        }
        else {
          paintCircle(position, getColor(printElement), false);
        }
      }

      if (printer != null) drawLogic(printElement.isSelected(), isUsual(printElement), getColor(printElement), printer);
    }
  }

  @Nullable
  @Override
  public PrintElement mouseOver(@NotNull Collection<? extends PrintElement> printElements, int x, int y) {
    for (PrintElement printElement : printElements) {
      if (printElement instanceof NodePrintElement) {
        if (PositionUtil.overNode(printElement.getPositionInCurrentRow(), x, y, getRowHeight())) {
          return printElement;
        }
      }
    }

    for (PrintElement printElement : printElements) {
      if (printElement instanceof EdgePrintElement) {
        EdgePrintElement edgePrintElement = (EdgePrintElement)printElement;
        if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
          if (PositionUtil.overDownEdge(edgePrintElement.getPositionInCurrentRow(), edgePrintElement.getPositionInOtherRow(), x, y, getRowHeight())) {
            return printElement;
          }
        }
        else {
          if (PositionUtil.overUpEdge(edgePrintElement.getPositionInOtherRow(), edgePrintElement.getPositionInCurrentRow(), x, y, getRowHeight())) {
            return printElement;
          }
        }
      }
    }
    return null;
  }
}
