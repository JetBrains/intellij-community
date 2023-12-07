// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.VcsScopeKt;
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan.LogData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.HeadCommitsComparator;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.computeWithSpan;

public class DataPack extends DataPackBase {
  public static final DataPack EMPTY = new DataPack(RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE),
                                                    EmptyPermanentGraph.getInstance(), Collections.emptyMap(), false);

  private final @NotNull PermanentGraph<Integer> myPermanentGraph;

  DataPack(@NotNull RefsModel refsModel,
           @NotNull PermanentGraph<Integer> permanentGraph,
           @NotNull Map<VirtualFile, VcsLogProvider> providers,
           boolean full) {
    super(providers, refsModel, full);
    myPermanentGraph = permanentGraph;
  }

  public static @NotNull DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                                        @NotNull Map<VirtualFile, CompressedRefs> refs,
                                        @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                        @NotNull VcsLogStorage storage,
                                        boolean full) {
    RefsModel refsModel = new RefsModel(refs, getHeads(commits), storage, providers);
    PermanentGraph<Integer> permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage);

    return new DataPack(refsModel, permanentGraph, providers, full);
  }

  protected static @NotNull PermanentGraph<Integer> buildPermanentGraph(@NotNull List<? extends GraphCommit<Integer>> commits,
                                                                        @NotNull RefsModel refsModel,
                                                                        @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                                        @NotNull VcsLogStorage storage) {
    PermanentGraph<Integer> permanentGraph;
    if (commits.isEmpty()) {
      permanentGraph = EmptyPermanentGraph.getInstance();
    }
    else {
      Comparator<Integer> headCommitdComparator = new HeadCommitsComparator(refsModel, getRefManagerMap(providers),
                                                                            VcsLogStorageImpl.createHashGetter(storage));
      Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getBranches(), storage);

      permanentGraph =
        computeWithSpan(TelemetryManager.getInstance().getTracer(VcsScopeKt.VcsScope), LogData.BuildingGraph.getName(), (span) -> {
          return PermanentGraphImpl.newInstance(commits, new GraphColorManagerImpl(refsModel), headCommitdComparator, branches);
        });
    }

    return permanentGraph;
  }

  private static @NotNull Set<Integer> getHeads(@NotNull List<? extends GraphCommit<Integer>> commits) {
    IntSet parents = new IntOpenHashSet();
    for (GraphCommit<Integer> commit : commits) {
      for (int parent : commit.getParents()) {
        parents.add(parent);
      }
    }

    Set<Integer> heads = new HashSet<>();
    for (GraphCommit<Integer> commit : commits) {
      if (!parents.contains((int)commit.getId())) {
        heads.add(commit.getId());
      }
    }
    return heads;
  }

  private static @NotNull Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<? extends VcsRef> branches,
                                                                  @NotNull VcsLogStorage storage) {
    Set<Integer> result = new HashSet<>();
    for (VcsRef vcsRef : branches) {
      result.add(storage.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()));
    }
    return result;
  }

  public static @NotNull Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = new HashMap<>();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  public @NotNull PermanentGraph<Integer> getPermanentGraph() {
    return myPermanentGraph;
  }

  @Override
  public @NonNls String toString() {
    return "{DataPack. " + myPermanentGraph.getAllCommits().size() + " commits in " + myLogProviders.keySet().size() + " roots}";
  }

  public static class SmallDataPack extends DataPack {
    private SmallDataPack(@NotNull RefsModel refsModel,
                          @NotNull PermanentGraph<Integer> permanentGraph,
                          @NotNull Map<VirtualFile, VcsLogProvider> providers) {
      super(refsModel, permanentGraph, providers, false);
    }

    public static @NotNull DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                                          @NotNull Map<VirtualFile, CompressedRefs> refs,
                                          @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                          @NotNull VcsLogStorage storage) {
      RefsModel refsModel = new RefsModel(refs, getHeads(commits), storage, providers);
      PermanentGraph<Integer> permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage);

      return new SmallDataPack(refsModel, permanentGraph, providers);
    }
  }

  public static class ErrorDataPack extends DataPack {
    private final @NotNull Throwable myError;

    public ErrorDataPack(@NotNull Throwable error) {
      super(RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE), EmptyPermanentGraph.getInstance(), Collections.emptyMap(), false);
      myError = error;
    }

    public @NotNull Throwable getError() {
      return myError;
    }
  }
}
