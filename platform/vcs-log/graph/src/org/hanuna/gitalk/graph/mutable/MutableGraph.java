package org.hanuna.gitalk.graph.mutable;

import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableGraph implements Graph {
  public static final GraphDecorator ID_DECORATOR = new GraphDecorator() {
    @Override
    public boolean isVisibleNode(@NotNull Node node) {
      return true;
    }

    @NotNull
    @Override
    public List<Edge> getDownEdges(@NotNull Node node, @NotNull List<Edge> innerDownEdges) {
      return innerDownEdges;
    }

    @NotNull
    @Override
    public List<Edge> getUpEdges(@NotNull Node node, @NotNull List<Edge> innerUpEdges) {
      return innerUpEdges;
    }

  };

  private final List<MutableNodeRow> allRows = new ArrayList<MutableNodeRow>();
  private final List<MutableNodeRow> visibleRows = new ArrayList<MutableNodeRow>();
  private GraphDecorator graphDecorator = ID_DECORATOR;

  public GraphDecorator getGraphDecorator() {
    return graphDecorator;
  }

  public void setGraphDecorator(GraphDecorator graphDecorator) {
    this.graphDecorator = graphDecorator;
  }

  @Override
  @NotNull
  public List<NodeRow> getNodeRows() {
    return Collections.<NodeRow>unmodifiableList(visibleRows);
  }

  @Nullable
  @Override
  public Node getCommitNodeInRow(int rowIndex) {
    if (rowIndex >= visibleRows.size()) {
      return null;
    }
    NodeRow nodeRow = visibleRows.get(rowIndex);
    for (Node node : nodeRow.getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE || node.getType() == Node.NodeType.END_COMMIT_NODE) {
        return node;
      }
    }
    return null;
  }

  public List<MutableNodeRow> getAllRows() {
    return allRows;
  }

  public void updateVisibleRows() {
    visibleRows.clear();
    for (MutableNodeRow row : allRows) {
      if (!row.getNodes().isEmpty()) {
        row.setRowIndex(visibleRows.size());
        visibleRows.add(row);
      }
    }
  }


}
