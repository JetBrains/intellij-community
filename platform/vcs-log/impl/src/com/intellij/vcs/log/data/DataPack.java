package com.intellij.vcs.log.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.graphmodel.impl.GraphModelImpl;
import com.intellij.vcs.log.printmodel.GraphPrintCellModel;
import com.intellij.vcs.log.printmodel.impl.GraphPrintCellModelImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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

    GraphModel graphModel = new GraphModelImpl(graph);

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

}
