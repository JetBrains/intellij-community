package org.hanuna.gitalk.swing_ui.render.painters;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.swing_ui.render.PositionUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.*;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {
  private Graphics2D g2;
  private final Stroke usual = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke hide = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
  private final Stroke selectUsual = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke selectHide = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);

  private final Color markColor = Color.BLACK;

  private void paintUpLine(int from, int to, Color color) {
    int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
    int y2 = -HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x2, y2, x1, y1);
  }

  private void paintDownLine(int from, int to, Color color) {
    int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
    int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x1, y1, x2, y2);
  }


  private void paintCircle(int position, Color color, boolean select) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    if (select) {
      r = SELECT_CIRCLE_RADIUS;
    }
    Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
    g2.setColor(color);
    g2.fill(circle);
  }

  private void paintHide(int position, Color color) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 + r);
    g2.drawLine(x0, y0 + r, x0 + r, y0);
    g2.drawLine(x0, y0 + r, x0 - r, y0);
  }

  private void paintShow(int position, Color color) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 - r);
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

  @Override
  public void draw(Graphics2D g2, GraphPrintCell row) {
    this.g2 = g2;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (ShortEdge edge : row.getUpEdges()) {
      if (edge.isMarked()) {
        setStroke(edge.isUsual(), true);
        paintUpLine(edge.getDownPosition(), edge.getUpPosition(), markColor);
      }
      setStroke(edge.isUsual(), edge.isSelected());
      paintUpLine(edge.getDownPosition(), edge.getUpPosition(), ColorGenerator.getColor(edge.getEdge().getBranch()));
    }
    for (ShortEdge edge : row.getDownEdges()) {
      if (edge.isMarked()) {
        setStroke(edge.isUsual(), true);
        paintDownLine(edge.getUpPosition(), edge.getDownPosition(), markColor);
      }
      setStroke(edge.isUsual(), edge.isSelected());
      paintDownLine(edge.getUpPosition(), edge.getDownPosition(), ColorGenerator.getColor(edge.getEdge().getBranch()));
    }
    for (SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      Edge edge;
      switch (printElement.getType()) {
        case COMMIT_NODE:
          Node node = printElement.getGraphElement().getNode();
          assert node != null;
          if (printElement.isMarked()) {
            paintCircle(printElement.getPosition(), markColor, true);
          }
          paintCircle(printElement.getPosition(), ColorGenerator.getColor(node.getBranch()), printElement.isSelected());
          break;
        case UP_ARROW:
          edge = printElement.getGraphElement().getEdge();
          assert edge != null;
          if (printElement.isMarked()) {
            setStroke(edge.getType() == Edge.EdgeType.USUAL, true);
            paintShow(printElement.getPosition(), markColor);
          }
          setStroke(edge.getType() == Edge.EdgeType.USUAL, printElement.isSelected());
          paintShow(printElement.getPosition(), ColorGenerator.getColor(edge.getBranch()));
          break;
        case DOWN_ARROW:
          edge = printElement.getGraphElement().getEdge();
          assert edge != null;
          if (printElement.isMarked()) {
            setStroke(edge.getType() == Edge.EdgeType.USUAL, true);
            paintHide(printElement.getPosition(), markColor);
          }
          setStroke(edge.getType() == Edge.EdgeType.USUAL, printElement.isSelected());
          paintHide(printElement.getPosition(), ColorGenerator.getColor(edge.getBranch()));
          break;
        default:
          throw new IllegalStateException();
      }
    }

  }

  @Nullable
  @Override
  public GraphElement mouseOver(GraphPrintCell row, int x, int y) {
    for (SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE) {
        if (PositionUtil.overNode(printElement.getPosition(), x, y)) {
          return printElement.getGraphElement();
        }
      }
    }
    for (ShortEdge edge : row.getUpEdges()) {
      if (PositionUtil.overUpEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }
    for (ShortEdge edge : row.getDownEdges()) {
      if (PositionUtil.overDownEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }

    return null;
  }

  @Nullable
  @Override
  public SpecialPrintElement mouseOverArrow(GraphPrintCell row, int x, int y) {
    for (SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      if (printElement.getType() != SpecialPrintElement.Type.COMMIT_NODE) {
        if (PositionUtil.overNode(printElement.getPosition(), x, y)) {
          return printElement;
        }
      }
    }
    return null;
  }
}
