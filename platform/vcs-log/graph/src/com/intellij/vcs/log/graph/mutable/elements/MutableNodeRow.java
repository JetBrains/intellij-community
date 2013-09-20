package com.intellij.vcs.log.graph.mutable.elements;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import com.intellij.vcs.log.graph.mutable.GraphDecorator;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
  private final List<MutableNode> nodes = new SmartList<MutableNode>();
  private final MutableGraph graph;
  private int rowIndex;

  public MutableNodeRow(@NotNull MutableGraph graph, int rowIndex) {
    this.graph = graph;
    this.rowIndex = rowIndex;
  }

  public void setRowIndex(int rowIndex) {
    this.rowIndex = rowIndex;
  }

  @NotNull
  public GraphDecorator getGraphDecorator() {
    return graph.getGraphDecorator();
  }

  @NotNull
  public List<MutableNode> getInnerNodeList() {
    return nodes;
  }

  @NotNull
  @Override
  public List<Node> getNodes() {
    List<Node> visibleNodes = new ArrayList<Node>(nodes.size());
    for (Node node : nodes) {
      if (getGraphDecorator().isVisibleNode(node)) {
        visibleNodes.add(node);
      }
    }
    return visibleNodes;
  }

  @Override
  public int getRowIndex() {
    return rowIndex;
  }

  @Override
  public String toString() {
    return getNodes().toString();
  }
}
