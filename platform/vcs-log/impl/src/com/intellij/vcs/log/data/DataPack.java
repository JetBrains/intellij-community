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
import gnu.trove.TIntHashSet;
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
                        @NotNull final VcsLogHashMap hashMap,
                        boolean full) {
    RefsModel refsModel;
    PermanentGraph<Integer> permanentGraph;
    if (commits.isEmpty()) {
      refsModel = new RefsModel(refs, ContainerUtil.<Integer>newHashSet(), hashMap);
      permanentGraph = EmptyPermanentGraph.getInstance();
    }
    else {
      refsModel = new RefsModel(refs, getHeads(commits), hashMap);
      NotNullFunction<Integer, Hash> hashGetter = new NotNullFunction<Integer, Hash>() {
        @NotNull
        @Override
        public Hash fun(Integer commitIndex) {
          return hashMap.getCommitId(commitIndex).getHash();
        }
      };
      GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
      Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getBranches(), hashMap);

      StopWatch sw = StopWatch.start("building graph");
      permanentGraph = PermanentGraphImpl.newInstance(commits, colorManager, branches);
      sw.report();

    }

    return new DataPack(refsModel, permanentGraph, providers, full);
  }

  @NotNull
  private static Set<Integer> getHeads(@NotNull List<? extends GraphCommit<Integer>> commits) {
    TIntHashSet parents = new TIntHashSet();
    for (GraphCommit<Integer> commit : commits) {
      for (int parent : commit.getParents()) {
        parents.add(parent);
      }
    }

    Set<Integer> heads = ContainerUtil.newHashSet();
    for (GraphCommit<Integer> commit : commits) {
      if (!parents.contains(commit.getId())) {
        heads.add(commit.getId());
      }
    }
    return heads;
  }

  @NotNull
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<VcsRef> branches, @NotNull VcsLogHashMap hashMap) {
    Set<Integer> result = new HashSet<Integer>();
    for (VcsRef vcsRef : branches) {
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
    RefsModel emptyModel = new RefsModel(Collections.<VirtualFile, Set<VcsRef>>emptyMap(), ContainerUtil.<Integer>newHashSet(), VcsLogHashMapImpl.EMPTY);
    return new DataPack(emptyModel, EmptyPermanentGraph.getInstance(), Collections.<VirtualFile, VcsLogProvider>emptyMap(), false);
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
