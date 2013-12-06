package com.intellij.vcs.log.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.Predicate;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.graphmodel.impl.GraphModelImpl;
import com.intellij.vcs.log.printmodel.GraphPrintCellModel;
import com.intellij.vcs.log.printmodel.impl.GraphPrintCellModelImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author erokhins
 */
public class DataPack {

  @NotNull private final GraphModel myGraphModel;
  @NotNull private final RefsModel myRefsModel;
  @NotNull private final GraphPrintCellModel myPrintCellModel;
  private final NotNullFunction<Integer, Hash> myHashGetter;
  private final NotNullFunction<Hash, Integer> myIndexGetter;

  @NotNull
  public static DataPack build(@NotNull List<? extends GraphCommit> commits, @NotNull Collection<VcsRef> allRefs, @NotNull ProgressIndicator indicator,
                               NotNullFunction<Integer, Hash> hashGetter, NotNullFunction<Hash, Integer> indexGetter) {
    indicator.setText("Building graph...");

    MutableGraph graph = GraphBuilder.build(commits, allRefs);

    GraphModel graphModel = new GraphModelImpl(graph, allRefs);

    final GraphPrintCellModel printCellModel = new GraphPrintCellModelImpl(graphModel.getGraph());
    graphModel.addUpdateListener(new Consumer<UpdateRequest>() {
      @Override
      public void consume(UpdateRequest key) {
        printCellModel.recalculate(key);
      }
    });

    final RefsModel refsModel = new RefsModel(allRefs, indexGetter);
    graphModel.getFragmentManager().setUnconcealedNodeFunction(new Function<Node, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(@NotNull Node key) {
        if (key.getDownEdges().isEmpty() || key.getUpEdges().isEmpty() || refsModel.isBranchRef(key.getCommitIndex())) {
          return true;
        }
        else {
          return false;
        }
      }
    });
    return new DataPack(graphModel, refsModel, printCellModel, hashGetter, indexGetter);
  }

  private DataPack(@NotNull GraphModel graphModel, @NotNull RefsModel refsModel, @NotNull GraphPrintCellModel printCellModel,
                   NotNullFunction<Integer, Hash> hashGetter, NotNullFunction<Hash, Integer> indexGetter) {
    myGraphModel = graphModel;
    myRefsModel = refsModel;
    myPrintCellModel = printCellModel;
    myHashGetter = hashGetter;
    myIndexGetter = indexGetter;
  }

  public void appendCommits(@NotNull List<GraphCommit> commitParentsList) {
    myGraphModel.appendCommitsToGraph(commitParentsList);
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  @NotNull
  public GraphModel getGraphModel() {
    return myGraphModel;
  }

  @NotNull
  public GraphPrintCellModel getPrintCellModel() {
    return myPrintCellModel;
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
    return getGraphModel().getFragmentManager().getUpNodes(down).contains(up);
  }

  @NotNull
  public Set<Node> getUpRefNodes(@NotNull GraphElement graphElement) {
    Set<Node> nodes = new HashSet<Node>();
    for (Node node : getGraphModel().getFragmentManager().getUpNodes(graphElement)) {
      if (getRefsModel().isBranchRef(node.getCommitIndex())) {
        nodes.add(node);
      }
    }
    return nodes;
  }

  @Nullable
  public Node getNode(int rowIndex) {
    return getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
  }

  public int getRowByHash(Hash commitHash) {
    Node node = getNodeByHash(commitHash);
    return node == null ? -1 : node.getRowIndex();
  }

  @Nullable
  public Node getNodeByHash(Hash hash) {
    int index = myIndexGetter.fun(hash);
    Graph graph = getGraphModel().getGraph();
    for (int i = 0; i < graph.getNodeRows().size(); i++) {
      Node node = graph.getCommitNodeInRow(i);
      if (node != null && node.getCommitIndex() == index) {
        return node;
      }
    }
    return null;
  }

  @Nullable
  public Node getNodeByPartOfHash(@NotNull String hash) {
    Graph graph = getGraphModel().getGraph();
    for (int i = 0; i < graph.getNodeRows().size(); i++) {
      Node node = graph.getCommitNodeInRow(i);
      if (node != null && myHashGetter.fun(node.getCommitIndex()).asString().startsWith(hash.toLowerCase())) {
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
    return ancestor != child && getGraphModel().getFragmentManager().getUpNodes(ancestor).contains(child);
  }

  @Nullable
  public Node getCommonParent(Node a, Node b, Node c) {
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

  public List<Node> getCommitsInBranchAboveBase(Node base, Node branchHead) {
    List<Node> result = new ArrayList<Node>();
    Node node = branchHead;
    while (node != base) {
      result.add(node);
      // TODO: multiple edges must not appear
      // TODO: if there are no edges, we are in the wrong branch
      node = node.getDownEdges().get(0).getDownNode();
    }
    //Collections.reverse(result);
    return result;
  }

}
