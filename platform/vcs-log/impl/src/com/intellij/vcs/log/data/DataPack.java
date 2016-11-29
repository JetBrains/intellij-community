/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
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

public class DataPack extends DataPackBase {
  public static final DataPack EMPTY = createEmptyInstance();

  @NotNull private final PermanentGraph<Integer> myPermanentGraph;

  DataPack(@NotNull RefsModel refsModel,
           @NotNull PermanentGraph<Integer> permanentGraph,
           @NotNull Map<VirtualFile, VcsLogProvider> providers,
           boolean full) {
    super(providers, refsModel, full);
    myPermanentGraph = permanentGraph;
  }

  @NotNull
  static DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                        @NotNull Map<VirtualFile, CompressedRefs> refs,
                        @NotNull Map<VirtualFile, VcsLogProvider> providers,
                        @NotNull final VcsLogStorage hashMap,
                        boolean full) {
    RefsModel refsModel;
    PermanentGraph<Integer> permanentGraph;
    if (commits.isEmpty()) {
      refsModel = new RefsModel(refs, ContainerUtil.<Integer>newHashSet(), hashMap, providers);
      permanentGraph = EmptyPermanentGraph.getInstance();
    }
    else {
      refsModel = new RefsModel(refs, getHeads(commits), hashMap, providers);
      Function<Integer, Hash> hashGetter = createHashGetter(hashMap);
      GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
      Set<Integer> branches = getBranchCommitHashIndexes(refsModel.getBranches(), hashMap);

      StopWatch sw = StopWatch.start("building graph");
      permanentGraph = PermanentGraphImpl.newInstance(commits, colorManager, branches);
      sw.report();
    }

    return new DataPack(refsModel, permanentGraph, providers, full);
  }

  @NotNull
  public static Function<Integer, Hash> createHashGetter(@NotNull final VcsLogStorage hashMap) {
    return commitIndex -> {
      CommitId commitId = hashMap.getCommitId(commitIndex);
      if (commitId == null) return null;
      return commitId.getHash();
    };
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
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<VcsRef> branches, @NotNull VcsLogStorage hashMap) {
    Set<Integer> result = new HashSet<>();
    for (VcsRef vcsRef : branches) {
      result.add(hashMap.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()));
    }
    return result;
  }

  @NotNull
  public static Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  @NotNull
  private static DataPack createEmptyInstance() {
    RefsModel emptyModel =
      new RefsModel(ContainerUtil.newHashMap(), ContainerUtil.<Integer>newHashSet(), VcsLogStorageImpl.EMPTY, ContainerUtil.newHashMap());
    return new DataPack(emptyModel, EmptyPermanentGraph.getInstance(), Collections.<VirtualFile, VcsLogProvider>emptyMap(), false);
  }

  @NotNull
  public PermanentGraph<Integer> getPermanentGraph() {
    return myPermanentGraph;
  }

  @Override
  public String toString() {
    return "{DataPack. " + myPermanentGraph.getAllCommits().size() + " commits in " + myLogProviders.keySet().size() + " roots}";
  }
}
