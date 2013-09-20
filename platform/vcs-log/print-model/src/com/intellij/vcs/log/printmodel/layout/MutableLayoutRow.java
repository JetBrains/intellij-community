package com.intellij.vcs.log.printmodel.layout;

import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author erokhins
 */
class MutableLayoutRow implements LayoutRow {
  private final List<GraphElement> graphElements;
  private NodeRow nodeRow;

  public MutableLayoutRow() {
    graphElements = new LinkedList<GraphElement>();
  }

  public MutableLayoutRow(@NotNull LayoutRow layoutRow) {
    this.graphElements = new LinkedList<GraphElement>(layoutRow.getOrderedGraphElements());
    this.nodeRow = layoutRow.getGraphNodeRow();
  }

  // modifiable List
  @NotNull
  public List<GraphElement> getModifiableOrderedGraphElements() {
    return graphElements;
  }

  public void setNodeRow(@NotNull NodeRow nodeRow) {
    this.nodeRow = nodeRow;
  }

  @NotNull
  @Override
  public List<GraphElement> getOrderedGraphElements() {
    return Collections.unmodifiableList(graphElements);
  }

  @Override
  public NodeRow getGraphNodeRow() {
    return nodeRow;
  }
}
