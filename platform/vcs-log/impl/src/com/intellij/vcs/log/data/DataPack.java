// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.util.StopWatch;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataPack extends DataPackBase {
  public static final DataPack EMPTY = new DataPack(RefsModel.createEmptyInstance(VcsLogStorageImpl.EMPTY),
                                                    EmptyPermanentGraph.getInstance(), Collections.emptyMap(), false);

  @NotNull private final PermanentGraph<Integer> myPermanentGraph;

  DataPack(@NotNull RefsModel refsModel,
           @NotNull PermanentGraph<Integer> permanentGraph,
           @NotNull Map<VirtualFile, VcsLogProvider> providers,
           boolean full) {
    super(providers, refsModel, full);
    myPermanentGraph = permanentGraph;
  }

  @NotNull
  public static DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                               @NotNull Map<VirtualFile, CompressedRefs> refs,
                               @NotNull Map<VirtualFile, VcsLogProvider> providers,
                               @NotNull VcsLogStorage storage,
                               boolean full) {
    RefsModel refsModel;
    PermanentGraph<Integer> permanentGraph;
    if (commits.isEmpty()) {
      refsModel = new RefsModel(refs, new HashSet<>(), storage, providers);
      permanentGraph = EmptyPermanentGraph.getInstance();
    }
    else {
      refsModel = new RefsModel(refs, getHeads(commits), storage, providers);
      Function<Integer, Hash> hashGetter = VcsLogStorageImpl.createHashGetter(storage);
      GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
      Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getBranches(), storage);

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

    Set<Integer> heads = new HashSet<>();
    for (GraphCommit<Integer> commit : commits) {
      if (!parents.contains(commit.getId())) {
        heads.add(commit.getId());
      }
    }
    return heads;
  }

  @NotNull
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<? extends VcsRef> branches, @NotNull VcsLogStorage storage) {
    Set<Integer> result = new HashSet<>();
    for (VcsRef vcsRef : branches) {
      result.add(storage.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()));
    }
    return result;
  }

  @NotNull
  public static Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = new HashMap<>();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  @NotNull
  public PermanentGraph<Integer> getPermanentGraph() {
    return myPermanentGraph;
  }

  @Override
  @NonNls
  public String toString() {
    return "{DataPack. " + myPermanentGraph.getAllCommits().size() + " commits in " + myLogProviders.keySet().size() + " roots}";
  }

  public static class ErrorDataPack extends DataPack {
    @NotNull private final Throwable myError;

    public ErrorDataPack(@NotNull Throwable error) {
      super(RefsModel.createEmptyInstance(VcsLogStorageImpl.EMPTY), EmptyPermanentGraph.getInstance(), Collections.emptyMap(), false);
      myError = error;
    }

    @NotNull
    public Throwable getError() {
      return myError;
    }
  }
}
