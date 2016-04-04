package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataPack {

  public static final DataPack EMPTY = createEmptyInstance();

  @NotNull private final RefsModel myRefsModel;
  @NotNull private final PermanentGraph<Integer> myPermanentGraph;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  private boolean myFull;

  DataPack(@NotNull RefsModel refsModel,
           @NotNull PermanentGraph<Integer> permanentGraph,
           @NotNull Map<VirtualFile, VcsLogProvider> providers,
           boolean full) {
    myRefsModel = refsModel;
    myPermanentGraph = permanentGraph;
    myLogProviders = providers;
    myFull = full;
  }

  @NotNull
  static DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                        @NotNull Map<VirtualFile, Set<VcsRef>> refs,
                        @NotNull Map<VirtualFile, VcsLogProvider> providers,
                        @NotNull VcsLogHashMap hashMap,
                        boolean full) {
    RefsModel refsModel = new RefsModel(refs, hashMap);
    PermanentGraph<Integer> graph = buildPermanentGraph(commits, refsModel, hashMap, providers);
    return new DataPack(refsModel, graph, providers, full);
  }

  @NotNull
  private static PermanentGraph<Integer> buildPermanentGraph(@NotNull List<? extends GraphCommit<Integer>> commits,
                                                             @NotNull RefsModel refsModel,
                                                             @NotNull final VcsLogHashMap hashMap,
                                                             @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    if (commits.isEmpty()) {
      return EmptyPermanentGraph.getInstance();
    }
    NotNullFunction<Integer, Hash> hashGetter = new NotNullFunction<Integer, Hash>() {
      @NotNull
      @Override
      public Hash fun(Integer commitIndex) {
        return hashMap.getCommitId(commitIndex).getHash();
      }
    };
    GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
    Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getAllRefs(), hashMap);
    StopWatch sw = StopWatch.start("building graph");
    PermanentGraphImpl<Integer> permanentGraph = PermanentGraphImpl.newInstance(commits, colorManager, branches);
    sw.report();
    return permanentGraph;
  }

  @NotNull
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<VcsRef> allRefs,
                                                         @NotNull VcsLogHashMap hashMap) {
    Set<Integer> result = new HashSet<Integer>();
    for (VcsRef vcsRef : allRefs) {
      if (vcsRef.getType().isBranch())
        result.add(hashMap.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()));
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

  @NotNull
  private static DataPack createEmptyInstance() {
    RefsModel emptyModel = new RefsModel(Collections.<VirtualFile, Set<VcsRef>>emptyMap(), VcsLogHashMapImpl.EMPTY);
    return new DataPack(emptyModel, EmptyPermanentGraph.getInstance(), Collections.<VirtualFile, VcsLogProvider>emptyMap(), false);
  }

  @NotNull
  public VcsLogRefs getRefs() {
    return myRefsModel;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  @NotNull
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

  @Override
  public String toString() {
    return "{DataPack. " + myPermanentGraph.getAllCommits().size() + " commits in " + myLogProviders.keySet().size() + " roots}";
  }
}
