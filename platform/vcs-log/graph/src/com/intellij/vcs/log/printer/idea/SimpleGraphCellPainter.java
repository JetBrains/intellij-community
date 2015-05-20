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

import com.intellij.ui.JBColor;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.SimplePrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Collection;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {

  private static final Color MARK_COLOR = JBColor.BLACK;
  private static final int ROW_HEIGHT = 24;

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

  private void paintUpLine(int from, int to, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
    int y1 = getRowHeight() / 2;
    int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
    int y2 = -getRowHeight() / 2;
    g2.setColor(color);
    g2.drawLine(x2, y2, x1, y1);
  }

  private void paintDownLine(int from, int to, Color color) {
    int x1 = PrintParameters.WIDTH_NODE * from + PrintParameters.WIDTH_NODE / 2;
    int y1 = getRowHeight() / 2;
    int x2 = PrintParameters.WIDTH_NODE * to + PrintParameters.WIDTH_NODE / 2;
    int y2 = getRowHeight() + getRowHeight() / 2;
    g2.setColor(color);
    g2.drawLine(x1, y1, x2, y2);
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

  private void paintDownArrow(int position, Color color) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    int y0 = getRowHeight() - r - 2;
    g2.setColor(color);
    g2.drawLine(x0, getRowHeight() / 2, x0, y0 + r);
    g2.drawLine(x0, y0 + r, x0 + r, y0);
    g2.drawLine(x0, y0 + r, x0 - r, y0);
  }

  private void paintUpArrow(int position, Color color) {
    int x0 = PrintParameters.WIDTH_NODE * position + PrintParameters.WIDTH_NODE / 2;
    int r = PrintParameters.CIRCLE_RADIUS;
    int y0 = r + 2;
    g2.setColor(color);
    g2.drawLine(x0, getRowHeight() / 2, x0, y0 - r);
    g2.drawLine(x0, y0 - r, x0 + r, y0);
    g2.drawLine(x0, y0 - r, x0 - r, y0);
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
              paintDownLine(from, to, color);
            }
            else {
              paintUpLine(from, to, color);
            }
          }
        };
      }

      if (printElement instanceof SimplePrintElement) {
        final int position = printElement.getPositionInCurrentRow();
        switch (((SimplePrintElement)printElement).getType()) {
          case NODE:
            if (printElement.isSelected()) {
              paintCircle(position, MARK_COLOR, true);
              paintCircle(position, getColor(printElement), false);
            }
            else {
              paintCircle(position, getColor(printElement), false);
            }
            break;
          case UP_ARROW:
            printer = new LitePrinter() {
              @Override
              public void print(Color color) {
                paintUpArrow(position, color);
              }
            };
            break;
          case DOWN_ARROW:
            printer = new LitePrinter() {
              @Override
              public void print(Color color) {
                paintDownArrow(position, color);
              }
            };
            break;
        }
      }

      if (printer != null) drawLogic(printElement.isSelected(), isUsual(printElement), getColor(printElement), printer);
    }
  }

  @Nullable
  @Override
  public PrintElement mouseOver(@NotNull Collection<? extends PrintElement> printElements, int x, int y) {
    for (PrintElement printElement : printElements) {
      if (printElement instanceof SimplePrintElement) {
        if (PositionUtil.overNode(printElement.getPositionInCurrentRow(), x, y, ((SimplePrintElement)printElement).getType(), getRowHeight())) {
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
