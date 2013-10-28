package com.intellij.vcs.log.printmodel.impl;

import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.printmodel.layout.LayoutModel;
import com.intellij.vcs.log.printmodel.layout.LayoutRow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.vcs.log.printmodel.impl.GraphElementsVisibilityController.VisibilityType.*;

/**
 * @author erokhins
 */
class GraphElementsVisibilityController {
  private static final int LONG_EDGE = 20;
  private static final int EDGE_PART_SHOW = 1;


  private final LayoutModel layoutModel;
  private boolean hideLongEdge;

  public GraphElementsVisibilityController(boolean hideLongEdge, LayoutModel layoutModel) {
    this.hideLongEdge = hideLongEdge;
    this.layoutModel = layoutModel;
  }

  public void setHideLongEdge(boolean hideLongEdge) {
    this.hideLongEdge = hideLongEdge;
  }

  @NotNull
  public VisibilityType visibilityTypeEdge(Edge edge, int rowIndex) {
    if (!hideLongEdge) {
      return USUAL;
    }
    int upRowIndex = edge.getUpNode().getRowIndex();
    int downRowIndex = edge.getDownNode().getRowIndex();
    if (downRowIndex - upRowIndex < LONG_EDGE) {
      return USUAL;
    }

    final int upDelta = rowIndex - upRowIndex;
    final int downDelta = downRowIndex - rowIndex;
    if (upDelta < EDGE_PART_SHOW || downDelta < EDGE_PART_SHOW) {
      return USUAL;
    }
    if (upDelta == EDGE_PART_SHOW) {
      return LAST_VISIBLE;
    }
    if (downDelta == EDGE_PART_SHOW) {
      return FIRST_VISIBLE;
    }

    return HIDE;
  }

  @NotNull
  public List<GraphElement> visibleElements(int rowIndex) {
    if (rowIndex < 0 || rowIndex >= layoutModel.getLayoutRows().size()) {
      return Collections.emptyList();
    }
    LayoutRow cellRow = layoutModel.getLayoutRows().get(rowIndex);
    List<GraphElement> cells = cellRow.getOrderedGraphElements();
    if (!hideLongEdge) {
      return cells;
    }

    List<GraphElement> visibleElements = new ArrayList<GraphElement>();
    for (GraphElement cell : cells) {
      if (cell.getNode() != null) {
        visibleElements.add(cell);
      }
      else {
        Edge edge = cell.getEdge();
        if (edge == null) {
          throw new IllegalStateException();
        }
        if (visibilityTypeEdge(edge, rowIndex) != HIDE) {
          visibleElements.add(cell);
        }
      }
    }

    return Collections.unmodifiableList(visibleElements);
  }

  public static enum VisibilityType {
    USUAL,
    LAST_VISIBLE,
    FIRST_VISIBLE,
    HIDE
  }
}
