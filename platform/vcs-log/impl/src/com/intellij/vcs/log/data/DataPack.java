package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphFacade;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.printer.idea.ColorGenerator;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DataPack implements VcsLogDataPack {

  @NotNull private final RefsModel myRefsModel;
  @NotNull private final PermanentGraph<Integer> myPermanentGraph;
  @NotNull private final GraphFacade myGraphFacade;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  private boolean myFull;

  @NotNull
  static DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                        @NotNull Map<VirtualFile, Set<VcsRef>> refs,
                        @NotNull Map<VirtualFile, VcsLogProvider> providers,
                        @NotNull VcsLogHashMap hashMap,
                        boolean full) {
    RefsModel refsModel = new RefsModel(refs, hashMap.asIndexGetter());
    PermanentGraph<Integer> graph = buildPermanentGraph(commits, refsModel, hashMap.asIndexGetter(), hashMap.asHashGetter(), providers);
    return new DataPack(refsModel, graph, createGraphFacade(graph), providers, full);
  }

  @NotNull
  private static GraphFacade createGraphFacade(@NotNull PermanentGraph<Integer> permanentGraph) {
    GraphFacade facade;
    if (!permanentGraph.getAllCommits().isEmpty()) {
      ColorGenerator colorGenerator = new ColorGenerator() {
        @Override
        public Color getColor(int colorId) {
          return com.intellij.vcs.log.graph.ColorGenerator.getColor(colorId);
        }
      };
      facade = new DelegateGraphFacade(permanentGraph, colorGenerator);
    }
    else {
      facade = new EmptyGraphFacade();
    }
    return facade;
  }

  @NotNull
  private static PermanentGraph<Integer> buildPermanentGraph(@NotNull List<? extends GraphCommit<Integer>> commits,
                                                             @NotNull RefsModel refsModel,
                                                             @NotNull NotNullFunction<Hash, Integer> indexGetter,
                                                             @NotNull NotNullFunction<Integer, Hash> hashGetter,
                                                             @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    if (commits.isEmpty()) {
      return EmptyPermanentGraph.getInstance();
    }
    GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
    Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getAllRefs(), indexGetter);
    StopWatch sw = StopWatch.start("building graph");
    PermanentGraphImpl<Integer> permanentGraph = PermanentGraphImpl.newInstance(commits, colorManager, branches);
    sw.report();
    return permanentGraph;
  }

  @NotNull
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<VcsRef> allRefs,
                                                         @NotNull NotNullFunction<Hash, Integer> indexGetter) {
    Set<Integer> result = new HashSet<Integer>();
    for (VcsRef vcsRef : allRefs) {
      if (vcsRef.getType().isBranch())
        result.add(indexGetter.fun(vcsRef.getCommitHash()));
    }
    return result;
  }

  @NotNull
  private static Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  DataPack(@NotNull RefsModel refsModel, @NotNull PermanentGraph<Integer> permanentGraph, @NotNull GraphFacade graphFacade,
           @NotNull Map<VirtualFile, VcsLogProvider> providers, boolean full) {
    myRefsModel = refsModel;
    myPermanentGraph = permanentGraph;
    myGraphFacade = graphFacade;
    myLogProviders = providers;
    myFull = full;
  }

  @NotNull
  public GraphFacade getGraphFacade() {
    return myGraphFacade;
  }

  @NotNull
  @Override
  public VcsLogRefs getRefs() {
    return myRefsModel;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

  @NotNull
  public PermanentGraph<Integer> getPermanentGraph() {
    return myPermanentGraph;
  }

  public boolean isFull() {
    return myFull;
  }

}
