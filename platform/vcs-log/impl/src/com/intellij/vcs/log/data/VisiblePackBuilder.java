/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class VisiblePackBuilder {

  private static final Logger LOG = Logger.getInstance(VisiblePackBuilder.class);

  @NotNull private final VcsLogHashMap myHashMap;
  @NotNull private final Map<Hash, VcsCommitMetadata> myTopCommitsDetailsCache;
  @NotNull private final CommitDetailsGetter myCommitDetailsGetter;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;

  VisiblePackBuilder(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                     @NotNull VcsLogHashMap hashMap,
                     @NotNull Map<Hash, VcsCommitMetadata> topCommitsDetailsCache,
                     @NotNull CommitDetailsGetter detailsGetter) {
    myHashMap = hashMap;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myCommitDetailsGetter = detailsGetter;
    myLogProviders = providers;
  }

  @NotNull
  Pair<VisiblePack, CommitCountStage> build(@NotNull DataPack dataPack,
                                            @NotNull PermanentGraph.SortType sortType,
                                            @NotNull VcsLogFilterCollection filters,
                                            @NotNull CommitCountStage commitCount) {
    VcsLogHashFilter hashFilter = filters.getHashFilter();
    if (hashFilter != null && !hashFilter.getHashes().isEmpty()) { // hashes should be shown, no matter if they match other filters or not
      return Pair.create(applyHashFilter(dataPack, hashFilter.getHashes(), sortType), commitCount);
    }

    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();
    Condition<Integer> filter;
    boolean canRequestMore;
    if (!detailsFilters.isEmpty()) {
      List<Hash> matchingCommits = null;
      if (commitCount == CommitCountStage.INITIAL) {
        matchingCommits = filterInMemory(dataPack.getPermanentGraph(), detailsFilters);
        if (matchingCommits.size() < commitCount.getCount()) {
          commitCount = commitCount.next();
          matchingCommits = null;
        }
      }

      if (matchingCommits == null) {
        try {
          matchingCommits = getFilteredDetailsFromTheVcs(myLogProviders, filters, commitCount.getCount());
        }
        catch (VcsException e) {
          // TODO show an error balloon or something else for non-ea guys.
          matchingCommits = Collections.emptyList();
          LOG.error(e);
        }
      }

      filter = getFilterFromCommits(matchingCommits);
      canRequestMore = matchingCommits.size() >= commitCount.getCount(); // from VCS: only "==", but from memory can be ">"
    }
    else {
      filter = null;
      canRequestMore = false;
    }

    Set<Integer> heads = getMatchingHeads(dataPack.getRefs(), filters);
    VisibleGraph<Integer> visibleGraph = dataPack.getPermanentGraph().createVisibleGraph(sortType, heads, filter);
    return Pair.create(new VisiblePack(dataPack, visibleGraph, canRequestMore), commitCount);
  }

  private VisiblePack applyHashFilter(@NotNull DataPack dataPack,
                                       @NotNull Collection<String> hashes,
                                       @NotNull PermanentGraph.SortType sortType) {
    final Set<Integer> indices = ContainerUtil.map2SetNotNull(hashes, new Function<String, Integer>() {
      @Override
      public Integer fun(String partOfHash) {
        Hash hash = myHashMap.findHashByString(partOfHash);
        return hash != null ? myHashMap.getCommitIndex(hash) : null;
      }
    });
    VisibleGraph<Integer> visibleGraph = dataPack.getPermanentGraph().createVisibleGraph(sortType, null, new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return indices.contains(integer);
      }
    });
    return new VisiblePack(dataPack, visibleGraph, false);
  }

  @Nullable
  private Set<Integer> getMatchingHeads(@NotNull VcsLogRefs refs, @NotNull VcsLogFilterCollection filters) {
    VcsLogBranchFilter branchFilter = filters.getBranchFilter();
    if (branchFilter == null) {
      return null;
    }

    final Collection<String> branchNames = new HashSet<String>(branchFilter.getBranchNames());
    return new HashSet<Integer>(ContainerUtil.mapNotNull(refs.getBranches(), new Function<VcsRef, Integer>() {
      @Override
      public Integer fun(VcsRef ref) {
        if (branchNames.contains(ref.getName())) {
          return myHashMap.getCommitIndex(ref.getCommitHash());
        }
        return null;
      }
    }));
  }

  @NotNull
  private List<Hash> filterInMemory(@NotNull PermanentGraph<Integer> permanentGraph, @NotNull List<VcsLogDetailsFilter> detailsFilters) {
    List<Hash> result = ContainerUtil.newArrayList();
    for (GraphCommit<Integer> commit : permanentGraph.getAllCommits()) {
      VcsCommitMetadata data = getDetailsFromCache(commit.getId());
      if (data == null) {
        // no more continuous details in the cache
        break;
      }
      if (matchesAllFilters(data, detailsFilters)) {
        result.add(data.getId());
      }
    }
    return result;
  }

  private static boolean matchesAllFilters(@NotNull final VcsCommitMetadata commit, @NotNull List<VcsLogDetailsFilter> detailsFilters) {
    return ContainerUtil.and(detailsFilters, new Condition<VcsLogDetailsFilter>() {
      @Override
      public boolean value(VcsLogDetailsFilter filter) {
        return filter.matches(commit);
      }
    });
  }

  @Nullable
  private VcsCommitMetadata getDetailsFromCache(final int commitIndex) {
    final Hash hash = myHashMap.getHash(commitIndex);
    VcsCommitMetadata details = myTopCommitsDetailsCache.get(hash);
    if (details != null) {
      return details;
    }
    return UIUtil.invokeAndWaitIfNeeded(new Computable<VcsCommitMetadata>() {
      @Override
      public VcsCommitMetadata compute() {
        return myCommitDetailsGetter.getCommitDataIfAvailable(hash);
      }
    });
  }

  @NotNull
  private static List<Hash> getFilteredDetailsFromTheVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                         @NotNull VcsLogFilterCollection filterCollection,
                                                         int maxCount) throws VcsException {
    Collection<List<TimedVcsCommit>> logs = ContainerUtil.newArrayList();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      VirtualFile root = entry.getKey();

      if (filterCollection.getStructureFilter() != null && filterCollection.getStructureFilter().getFiles(root).isEmpty()
          || filterCollection.getUserFilter() != null && filterCollection.getUserFilter().getUserNames(root).isEmpty()) {
        // there is a structure or user filter, but it doesn't match this root
        continue;
      }

      List<TimedVcsCommit> matchingCommits = entry.getValue().getCommitsMatchingFilter(root, filterCollection, maxCount);
      logs.add(matchingCommits);
    }

    List<TimedVcsCommit> compoundLog = new VcsLogMultiRepoJoiner<Hash, TimedVcsCommit>().join(logs);
    return ContainerUtil.map(compoundLog, new Function<TimedVcsCommit, Hash>() {
      @Override
      public Hash fun(TimedVcsCommit commit) {
        return commit.getId();
      }
    });
  }

  @Nullable
  private Condition<Integer> getFilterFromCommits(@Nullable List<Hash> filteredCommits) {
    if (filteredCommits == null) {
      return null;
    }

    final Set<Integer> commitSet = ContainerUtil.map2Set(filteredCommits, new Function<Hash, Integer>() {
      @Override
      public Integer fun(Hash hash) {
        return myHashMap.getCommitIndex(hash);
      }
    });
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return commitSet.contains(integer);
      }
    };
  }

}
