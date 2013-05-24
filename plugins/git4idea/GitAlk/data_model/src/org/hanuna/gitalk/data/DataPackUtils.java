package org.hanuna.gitalk.data;

import com.intellij.util.containers.Predicate;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    Node node = getNodeByHash(commitHash);
    return node == null ? -1 : node.getRowIndex();
  }

  @Nullable
  public Node getNodeByHash(Hash hash) {
    Graph graph = dataPack.getGraphModel().getGraph();
    for (int i = 0; i < graph.getNodeRows().size(); i++) {
      Node node = graph.getCommitNodeInRow(i);
      if (node != null && node.getCommitHash().equals(hash)) {
        return node;
      }
    }
    return null;
  }

  @Nullable
  public Node getCommonParent(Node a, Node b) {
    List<Node> commitDiff = getCommitsDownToCommon(a, b);
    return commitDiff.isEmpty() ? null : commitDiff.get(commitDiff.size() - 1);
  }

  public boolean isAncestorOf(Node ancestor, Node child) {
    return dataPack.getGraphModel().getFragmentManager().getUpNodes(ancestor).contains(child);
  }

  @Nullable
  public Node getCommonParent(Node a, Node b, Node c) {
    if (isAncestorOf(a, b)) {
      if (isAncestorOf(a, c)) return a;
    }
    if (isAncestorOf(b, a) && isAncestorOf(b, c)) return b;
    if (isAncestorOf(c, a) && isAncestorOf(c, b)) return c;
    return null;
  }

  public List<Node> getCommitsDownToCommon(Node newBase, Node head) {
    final List<Node> all = getAllAncestors(newBase, new Predicate<Node>() {
      @Override
      public boolean apply(@Nullable Node input) {
        return false;
      }
    });
    return getAllAncestors(head, new Predicate<Node>() {
      @Override
      public boolean apply(@Nullable Node input) {
        return all.contains(input);
      }
    });
  }

  private List<Node> getAllAncestors(Node a, Predicate<Node> stop) {
    Set<Node> all = new LinkedHashSet<Node>();
    Queue<Node> queue = new ArrayDeque<Node>();
    queue.add(a);
    while (!queue.isEmpty()) {
      Node aNode = queue.remove();
      all.add(aNode);
      if (stop.apply(aNode)) {
        return new ArrayList<Node>(all);
      }
      for (Edge edge : aNode.getDownEdges()) {
        queue.add(edge.getDownNode());
      }
    }
    return new ArrayList<Node>(all);
  }

  @Nullable
  public Ref findRefOfNode(Node node) {
    for (Ref ref : this.dataPack.getRefsModel().getAllRefs()) {
      if (ref.getCommitHash().equals(node.getCommitHash())) {
        return ref;
      }
    }
    return null;
  }

  public List<Node> getCommitsInBranchAboveBase(Node firstToInclude, Ref branchLabel) {
    List<Node> result = new ArrayList<Node>();
    Node node = firstToInclude;
    while (true) {
      if (node != firstToInclude) {
        result.add(node);
      }
      if (node.getCommitHash().equals(branchLabel.getCommitHash())) {
        break;
      }
      // TODO: multiple edges must not appear
      // TODO: if there are no edges, we are in the wrong branch
      node = node.getUpEdges().get(0).getUpNode();
    }
    return result;
  }
}
