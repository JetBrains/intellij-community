// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Collection;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {
  private static final Color MARK_COLOR = JBColor.BLACK;
  private static final double ARROW_ANGLE_COS2 = 0.7;
  private static final double ARROW_LENGTH = 0.3;

  private final @NotNull ColorGenerator myColorGenerator;

  public SimpleGraphCellPainter(@NotNull ColorGenerator colorGenerator) {
    myColorGenerator = colorGenerator;
  }

  protected int getRowHeight() {
    return PaintParameters.ROW_HEIGHT;
  }

  private float[] getDashLength(double edgeLength) {
    // If the edge is vertical, then edgeLength is equal to rowHeight. Exactly one dash and one space fits on the edge,
    // so spaceLength + dashLength is also equal to rowHeight.
    // When the edge is not vertical, spaceLength is kept the same, but dashLength is chosen to be slightly greater
    // so that the whole number of dashes would fit on the edge.

    int rowHeight = getRowHeight();
    int dashCount = Math.max(1, (int)Math.floor(edgeLength / rowHeight));
    float spaceLength = rowHeight / 2.0f - 2;
    float dashLength = (float)(edgeLength / dashCount - spaceLength);
    return new float[]{dashLength, spaceLength};
  }

  private @NotNull BasicStroke getOrdinaryStroke() {
    return new BasicStroke(PaintParameters.getLineThickness(getRowHeight()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  }

  private @NotNull BasicStroke getSelectedStroke() {
    return new BasicStroke(PaintParameters.getSelectedLineThickness(getRowHeight()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  }

  private @NotNull Stroke getDashedStroke(float[] dash) {
    return new BasicStroke(PaintParameters.getLineThickness(getRowHeight()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, dash,
                           dash[0] / 2);
  }

  private @NotNull Stroke getSelectedDashedStroke(float[] dash) {
    return new BasicStroke(PaintParameters.getSelectedLineThickness(getRowHeight()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, dash,
                           dash[0] / 2);
  }

  private void paintUpLine(@NotNull Graphics2D g2,
                           @NotNull Color color,
                           int from,
                           int to,
                           boolean hasArrow,
                           boolean isUsual,
                           boolean isSelected,
                           boolean isTerminal) {
    // paint vertical lines normal size
    // paint non-vertical lines twice the size to make them dock with each other well
    int nodeWidth = PaintParameters.getNodeWidth(getRowHeight());
    if (from == to) {
      int x = nodeWidth * from + nodeWidth / 2;
      int y1 = getRowHeight() / 2 - 1;
      int y2 = isTerminal ? PaintParameters.getCircleRadius(getRowHeight()) / 2 + 1 : 0;
      paintLine(g2, color, hasArrow, x, y1, x, y2, x, y2, isUsual, isSelected);
    }
    else {
      assert !isTerminal;
      int x1 = nodeWidth * from + nodeWidth / 2;
      int y1 = getRowHeight() / 2;
      int x2 = nodeWidth * to + nodeWidth / 2;
      int y2 = -getRowHeight() / 2;
      paintLine(g2, color, hasArrow, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2, isUsual, isSelected);
    }
  }

  private void paintDownLine(@NotNull Graphics2D g2,
                             @NotNull Color color,
                             int from,
                             int to,
                             boolean hasArrow,
                             boolean isUsual,
                             boolean isSelected,
                             boolean isTerminal) {
    int nodeWidth = PaintParameters.getNodeWidth(getRowHeight());
    if (from == to) {
      int y2 = getRowHeight() - (isTerminal ? PaintParameters.getCircleRadius(getRowHeight()) / 2 + 1 : 0);
      int y1 = getRowHeight() / 2;
      int x = nodeWidth * from + nodeWidth / 2;
      paintLine(g2, color, hasArrow, x, y1, x, y2, x, y2, isUsual, isSelected);
    }
    else {
      assert !isTerminal;
      int x1 = nodeWidth * from + nodeWidth / 2;
      int y1 = getRowHeight() / 2;
      int x2 = nodeWidth * to + nodeWidth / 2;
      int y2 = getRowHeight() + getRowHeight() / 2;
      paintLine(g2, color, hasArrow, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2, isUsual, isSelected);
    }
  }

  private void paintLine(@NotNull Graphics2D g2,
                         @NotNull Color color,
                         boolean hasArrow,
                         int x1,
                         int y1,
                         int x2,
                         int y2,
                         int startArrowX,
                         int startArrowY,
                         boolean isUsual,
                         boolean isSelected) {
    g2.setColor(color);

    if (isUsual || hasArrow) {
      setUsualStroke(g2, isSelected);
    }
    else {
      setDashedStroke(g2, isSelected, (x1 == x2) ? getRowHeight() : Math.hypot(x1 - x2, y1 - y2));
    }

    g2.drawLine(x1, y1, x2, y2);
    if (hasArrow) {
      Pair<Integer, Integer> rotate1 = rotate(x1, y1, startArrowX, startArrowY,
                                              Math.sqrt(ARROW_ANGLE_COS2), Math.sqrt(1 - ARROW_ANGLE_COS2),
                                              ARROW_LENGTH * getRowHeight());
      Pair<Integer, Integer> rotate2 = rotate(x1, y1, startArrowX, startArrowY,
                                              Math.sqrt(ARROW_ANGLE_COS2), -Math.sqrt(1 - ARROW_ANGLE_COS2),
                                              ARROW_LENGTH * getRowHeight());
      g2.drawLine(startArrowX, startArrowY, rotate1.first, rotate1.second);
      g2.drawLine(startArrowX, startArrowY, rotate2.first, rotate2.second);
    }
  }

  private static @NotNull Pair<Integer, Integer> rotate(double x,
                                                        double y,
                                                        double centerX,
                                                        double centerY,
                                                        double cos,
                                                        double sin,
                                                        double arrowLength) {
    double translateX = (x - centerX);
    double translateY = (y - centerY);

    double d = Math.hypot(translateX, translateY);
    double scaleX = arrowLength * translateX / d;
    double scaleY = arrowLength * translateY / d;

    double rotateX = scaleX * cos - scaleY * sin;
    double rotateY = scaleX * sin + scaleY * cos;

    return Pair.create((int)Math.round(rotateX + centerX), (int)Math.round(rotateY + centerY));
  }

  private void paintCircle(@NotNull Graphics2D g2, int position, @NotNull Color color, boolean select) {
    int nodeWidth = PaintParameters.getNodeWidth(getRowHeight());
    int circleRadius = PaintParameters.getCircleRadius(getRowHeight());
    int selectedCircleRadius = PaintParameters.getSelectedCircleRadius(getRowHeight());

    int x0 = nodeWidth * position + nodeWidth / 2;
    int y0 = getRowHeight() / 2;
    int r = circleRadius;
    if (select) {
      r = selectedCircleRadius;
    }
    Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
    g2.setColor(color);
    g2.fill(circle);
  }

  private void setUsualStroke(@NotNull Graphics2D g2, boolean select) {
    g2.setStroke(select ? getSelectedStroke() : getOrdinaryStroke());
  }

  private void setDashedStroke(@NotNull Graphics2D g2, boolean select, double edgeLength) {
    float[] length = getDashLength(edgeLength);
    g2.setStroke(select ? getSelectedDashedStroke(length) : getDashedStroke(length));
  }

  private @NotNull Color getColor(@NotNull PrintElement printElement, boolean isSelected) {
    if (isSelected) return MARK_COLOR;
    return myColorGenerator.getColor(printElement.getColorId());
  }

  private static boolean isUsual(@NotNull EdgePrintElement.LineStyle lineStyle) {
    return lineStyle == EdgePrintElement.LineStyle.SOLID;
  }

  @Override
  public void draw(@NotNull Graphics2D g2, @NotNull Collection<? extends PrintElement> printElements) {
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    List<PrintElement> selected = new SmartList<>();
    for (PrintElement printElement : printElements) {
      if (printElement.isSelected()) {
        selected.add(printElement); // to draw later
      }
      else {
        drawElement(g2, printElement, false);
      }
    }

    // draw selected elements
    for (PrintElement printElement : selected) {
      drawElement(g2, printElement, true);
    }

    for (PrintElement printElement : selected) {
      drawElement(g2, printElement, false);
    }
  }

  protected void drawElement(@NotNull Graphics2D g2, @NotNull PrintElement printElement, boolean isSelected) {
    if (printElement instanceof EdgePrintElement edgePrintElement) {
      printEdge(g2, getColor(printElement, isSelected), isSelected, edgePrintElement);
    }

    if (printElement instanceof NodePrintElement) {
      paintCircle(g2, printElement.getPositionInCurrentRow(), getColor(printElement, isSelected), isSelected);
    }
  }

  private void printEdge(@NotNull Graphics2D g2, @NotNull Color color, boolean isSelected, @NotNull EdgePrintElement edgePrintElement) {
    int from = edgePrintElement.getPositionInCurrentRow();
    int to = edgePrintElement.getPositionInOtherRow();
    boolean isUsual = isUsual(edgePrintElement.getLineStyle());
    boolean isTerminal = edgePrintElement instanceof TerminalEdgePrintElement;
    
    if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
      paintDownLine(g2, color, from, to, edgePrintElement.hasArrow(), isUsual, isSelected, isTerminal);
    }
    else {
      paintUpLine(g2, color, from, to, edgePrintElement.hasArrow(), isUsual, isSelected, isTerminal);
    }
  }

  @Override
  public @Nullable PrintElement getElementUnderCursor(@NotNull Collection<? extends PrintElement> printElements, int x, int y) {
    int nodeWidth = PaintParameters.getNodeWidth(getRowHeight());
    for (PrintElement printElement : printElements) {
      if (printElement instanceof NodePrintElement) {
        int circleRadius = PaintParameters.getCircleRadius(getRowHeight());
        if (PositionUtil.overNode(printElement.getPositionInCurrentRow(), x, y, getRowHeight(), nodeWidth, circleRadius)) {
          return printElement;
        }
      }
    }

    for (PrintElement printElement : printElements) {
      if (printElement instanceof EdgePrintElement edgePrintElement) {
        float lineThickness = PaintParameters.getLineThickness(getRowHeight());
        if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
          if (PositionUtil.overDownEdge(edgePrintElement.getPositionInCurrentRow(), edgePrintElement.getPositionInOtherRow(),
                                        x, y, getRowHeight(), nodeWidth, lineThickness)) {
            return printElement;
          }
        }
        else {
          if (PositionUtil.overUpEdge(edgePrintElement.getPositionInOtherRow(), edgePrintElement.getPositionInCurrentRow(),
                                      x, y, getRowHeight(), nodeWidth, lineThickness)) {
            return printElement;
          }
        }
      }
    }
    return null;
  }
}
