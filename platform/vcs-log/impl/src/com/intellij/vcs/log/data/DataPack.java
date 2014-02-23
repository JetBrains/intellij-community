package com.intellij.vcs.log.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphFacade;
import com.intellij.vcs.log.graph.GraphFacadeImpl;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.graphmodel.impl.GraphModelImpl;
import com.intellij.vcs.log.printmodel.GraphPrintCellModel;
import com.intellij.vcs.log.printmodel.impl.GraphPrintCellModelImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DataPack {

  @NotNull private final RefsModel myRefsModel;
  @NotNull private final GraphFacade myGraphFacade;

  @NotNull
  public static DataPack build(@NotNull List<? extends GraphCommit> commits,
                               @NotNull Collection<VcsRef> allRefs,
                               @NotNull ProgressIndicator indicator,
                               @NotNull NotNullFunction<Hash, Integer> indexGetter,
                               @NotNull NotNullFunction<Integer, Hash> hashGetter, 
                               @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
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
    GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(logProviders));
    return new DataPack(refsModel, new GraphFacadeImpl(graphModel, printCellModel, colorManager));
  }

  @NotNull
  private static Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  private DataPack(@NotNull RefsModel refsModel, @NotNull GraphFacade graphFacade) {
    myRefsModel = refsModel;
    myGraphFacade = graphFacade;
  }

  @NotNull
  public GraphFacade getGraphFacade() {
    return myGraphFacade;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

}
