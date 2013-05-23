package org.hanuna.gitalk.data;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class DataPackUtils {
  private final DataPack dataPack;

  public DataPackUtils(DataPack dataPack) {
    this.dataPack = dataPack;
  }

  public boolean isSameBranch(@NotNull Node nodeA, @NotNull Node nodeB) {
    Node up, down;
    if (nodeA.getRowIndex() > nodeB.getRowIndex()) {
      up = nodeB;
      down = nodeA;
    } else {
      up = nodeA;
      down = nodeB;
    }
    return dataPack.getGraphModel().getFragmentManager().getUpNodes(down).contains(up);
  }

  @NotNull
  public Set<Node> getUpRefNodes(@NotNull GraphElement graphElement) {
    Set<Node> nodes = new HashSet<Node>();
    for (Node node : dataPack.getGraphModel().getFragmentManager().getUpNodes(graphElement)) {
      if (dataPack.getRefsModel().isBranchRef(node.getCommitHash())) {
        nodes.add(node);
      }
    }
    return nodes;
  }

  @Nullable
  public Node getNode(int rowIndex) {
    return dataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
  }

  public int getRowByHash(Hash commitHash) {
    Graph graph = dataPack.getGraphModel().getGraph();
    int row = -1;
    for (int i = 0; i < graph.getNodeRows().size(); i++) {
      Node node = graph.getCommitNodeInRow(i);
      if (node != null && node.getCommitHash().equals(commitHash)) {
        row = i;
        break;
      }
    }
    return row;
  }

}
