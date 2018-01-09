/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.visible;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder;
import com.intellij.vcs.log.impl.VcsLogHashFilterImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(VcsLogFilterer.class);

  @NotNull protected final VcsLogStorage myStorage;
  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;
  @NotNull private final DataGetter<VcsFullCommitDetails> myCommitDetailsGetter;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull protected final VcsLogIndex myIndex;

  public VcsLogFilterer(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                        @NotNull VcsLogStorage storage,
                        @NotNull TopCommitsCache topCommitsDetailsCache,
                        @NotNull DataGetter<VcsFullCommitDetails> detailsGetter,
                        @NotNull VcsLogIndex index) {
    myStorage = storage;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myCommitDetailsGetter = detailsGetter;
    myLogProviders = providers;
    myIndex = index;
  }

  @NotNull
  protected Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack dataPack,
                                                       @NotNull PermanentGraph.SortType sortType,
                                                       @NotNull VcsLogFilterCollection filters,
                                                       @NotNull CommitCountStage commitCount) {
    long start = System.currentTimeMillis();

    VcsLogHashFilter hashFilter = filters.getHashFilter();
    if (hashFilter != null && !hashFilter.getHashes().isEmpty()) { // hashes should be shown, no matter if they match other filters or not
      return Pair.create(applyHashFilter(dataPack, hashFilter.getHashes(), sortType), commitCount);
    }

    Collection<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.getLogProviders().keySet(), filters);
    Set<Integer> matchingHeads = getMatchingHeads(dataPack.getRefsModel(), visibleRoots, filters);
    FilterByDetailsResult filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads);

    VisibleGraph<Integer> visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits);
    VisiblePack visiblePack = new VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters);

    LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for filtering by " + filters);
    return Pair.create(visiblePack, filterResult.commitCount);
  }

  @NotNull
  protected VisibleGraph<Integer> createVisibleGraph(@NotNull DataPack dataPack,
                                                     @NotNull PermanentGraph.SortType sortType,
                                                     @Nullable Set<Integer> matchingHeads,
                                                     @Nullable Set<Integer> matchingCommits) {
    if (matchesNothing(matchingHeads) || matchesNothing(matchingCommits)) {
      return EmptyVisibleGraph.getInstance();
    }
    else {
      return dataPack.getPermanentGraph().createVisibleGraph(sortType, matchingHeads, matchingCommits);
    }
  }

  @NotNull
  private FilterByDetailsResult filterByDetails(@NotNull DataPack dataPack,
                                                @NotNull VcsLogFilterCollection filters,
                                                @NotNull CommitCountStage commitCount,
                                                @NotNull Collection<VirtualFile> visibleRoots,
                                                @Nullable Set<Integer> matchingHeads) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();
    if (detailsFilters.isEmpty()) return new FilterByDetailsResult(null, false, commitCount);

    Set<Integer> filteredWidthIndex = null;
    if (myIndex.canFilter(detailsFilters)) {
      Collection<VirtualFile> notIndexedRoots = ContainerUtil.filter(visibleRoots, root -> !myIndex.isIndexed(root));

      if (notIndexedRoots.size() < visibleRoots.size()) {
        filteredWidthIndex = myIndex.filter(detailsFilters);
        if (notIndexedRoots.isEmpty()) return new FilterByDetailsResult(filteredWidthIndex, false, commitCount);
        matchingHeads = getMatchingHeads(dataPack.getRefsModel(), notIndexedRoots, filters);
      }
    }

    FilterByDetailsResult filteredWithVcs =
      filterWithVcs(dataPack.getPermanentGraph(), filters, detailsFilters, matchingHeads, commitCount);

    Set<Integer> filteredCommits;
    if (filteredWidthIndex == null) {
      filteredCommits = filteredWithVcs.matchingCommits;
    }
    else if (filteredWithVcs.matchingCommits == null) {
      filteredCommits = filteredWidthIndex;
    }
    else {
      filteredCommits = ContainerUtil.union(filteredWidthIndex, filteredWithVcs.matchingCommits);
    }
    return new FilterByDetailsResult(filteredCommits, filteredWithVcs.canRequestMore, filteredWithVcs.commitCount);
  }

  @NotNull
  private FilterByDetailsResult filterWithVcs(@NotNull PermanentGraph graph,
                                              @NotNull VcsLogFilterCollection filters,
                                              @NotNull List<VcsLogDetailsFilter> detailsFilters,
                                              @Nullable Set<Integer> matchingHeads,
                                              @NotNull CommitCountStage commitCount) {
    Set<Integer> matchingCommits = null;
    if (commitCount == CommitCountStage.INITIAL) {
      matchingCommits = getMatchedCommitIndex(filterInMemory(graph, detailsFilters, matchingHeads));
      if (matchingCommits.size() < commitCount.getCount()) {
        commitCount = commitCount.next();
        matchingCommits = null;
      }
    }

    if (matchingCommits == null) {
      try {
        matchingCommits = getMatchedCommitIndex(getFilteredDetailsFromTheVcs(myLogProviders, filters, commitCount.getCount()));
      }
      catch (VcsException e) {
        //TODO show an error balloon or something else for non-ea guys.
        matchingCommits = Collections.emptySet();
        LOG.error(e);
      }
    }

    return new FilterByDetailsResult(matchingCommits, matchingCommits.size() >= commitCount.getCount(), commitCount);
  }

  private static <T> boolean matchesNothing(@Nullable Collection<T> matchingSet) {
    return matchingSet != null && matchingSet.isEmpty();
  }

  @NotNull
  private VisiblePack applyHashFilter(@NotNull DataPack dataPack,
                                      @NotNull Collection<String> hashes,
                                      @NotNull PermanentGraph.SortType sortType) {
    Set<Integer> indices = ContainerUtil.map2SetNotNull(hashes, partOfHash -> {
      CommitId commitId = myStorage.findCommitId(new CommitIdByStringCondition(partOfHash));
      return commitId != null ? myStorage.getCommitIndex(commitId.getHash(), commitId.getRoot()) : null;
    });
    VisibleGraph<Integer> visibleGraph = dataPack.getPermanentGraph().createVisibleGraph(sortType, null, indices);
    return new VisiblePack(dataPack, visibleGraph, false,
                           new VcsLogFilterCollectionBuilder().with(new VcsLogHashFilterImpl(hashes)).build());
  }

  @Nullable
  protected Set<Integer> getMatchingHeads(@NotNull VcsLogRefs refs,
                                          @NotNull Collection<VirtualFile> roots,
                                          @NotNull VcsLogFilterCollection filters) {
    VcsLogBranchFilter branchFilter = filters.getBranchFilter();
    VcsLogRootFilter rootFilter = filters.getRootFilter();
    VcsLogStructureFilter structureFilter = filters.getStructureFilter();

    if (branchFilter == null && rootFilter == null && structureFilter == null) return null;

    Set<Integer> filteredByFile = getMatchingHeads(refs, roots);
    if (branchFilter == null) return filteredByFile;

    Set<Integer> filteredByBranch = getMatchingHeads(refs, branchFilter);
    return new HashSet<>(ContainerUtil.intersection(filteredByBranch, filteredByFile));
  }

  @NotNull
  private Set<Integer> getMatchingHeads(@NotNull VcsLogRefs refs, @NotNull VcsLogBranchFilter filter) {
    return ContainerUtil.map2SetNotNull(refs.getBranches(), ref -> {
      boolean acceptRef = filter.matches(ref.getName());
      return acceptRef ? myStorage.getCommitIndex(ref.getCommitHash(), ref.getRoot()) : null;
    });
  }

  @NotNull
  private Set<Integer> getMatchingHeads(@NotNull VcsLogRefs refs, @NotNull Collection<VirtualFile> roots) {
    Set<Integer> result = new HashSet<>();
    for (VcsRef branch : refs.getBranches()) {
      if (roots.contains(branch.getRoot())) {
        result.add(myStorage.getCommitIndex(branch.getCommitHash(), branch.getRoot()));
      }
    }
    return result;
  }

  @NotNull
  private Collection<CommitId> filterInMemory(@NotNull PermanentGraph<Integer> permanentGraph,
                                              @NotNull List<VcsLogDetailsFilter> detailsFilters,
                                              @Nullable Set<Integer> matchingHeads) {
    Collection<CommitId> result = ContainerUtil.newArrayList();
    for (GraphCommit<Integer> commit : permanentGraph.getAllCommits()) {
      VcsCommitMetadata data = getDetailsFromCache(commit.getId());
      if (data == null) {
        // no more continuous details in the cache
        break;
      }
      if (matchesAllFilters(data, permanentGraph, detailsFilters, matchingHeads)) {
        result.add(new CommitId(data.getId(), data.getRoot()));
      }
    }
    return result;
  }

  private boolean matchesAllFilters(@NotNull final VcsCommitMetadata commit,
                                    @NotNull final PermanentGraph<Integer> permanentGraph,
                                    @NotNull List<VcsLogDetailsFilter> detailsFilters,
                                    @Nullable final Set<Integer> matchingHeads) {
    boolean matchesAllDetails = ContainerUtil.and(detailsFilters, filter -> filter.matches(commit));
    return matchesAllDetails && matchesAnyHead(permanentGraph, commit, matchingHeads);
  }

  private boolean matchesAnyHead(@NotNull PermanentGraph<Integer> permanentGraph,
                                 @NotNull VcsCommitMetadata commit,
                                 @Nullable Set<Integer> matchingHeads) {
    if (matchingHeads == null) {
      return true;
    }
    // TODO O(n^2)
    int commitIndex = myStorage.getCommitIndex(commit.getId(), commit.getRoot());
    return ContainerUtil.intersects(permanentGraph.getContainingBranches(commitIndex), matchingHeads);
  }

  @Nullable
  private VcsCommitMetadata getDetailsFromCache(final int commitIndex) {
    VcsCommitMetadata details = myTopCommitsDetailsCache.get(commitIndex);
    if (details != null) {
      return details;
    }
    return UIUtil.invokeAndWaitIfNeeded((Computable<VcsCommitMetadata>)() -> myCommitDetailsGetter.getCommitDataIfAvailable(commitIndex));
  }

  @NotNull
  private static Collection<CommitId> getFilteredDetailsFromTheVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                                   @NotNull VcsLogFilterCollection filterCollection,
                                                                   int maxCount) throws VcsException {
    Set<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(providers.keySet(), filterCollection);

    Collection<CommitId> commits = ContainerUtil.newArrayList();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      final VirtualFile root = entry.getKey();

      if (!visibleRoots.contains(root) ||
          (filterCollection.getUserFilter() != null && filterCollection.getUserFilter().getUsers(root).isEmpty())) {
        // there is a structure or user filter, but it doesn't match this root
        continue;
      }

      VcsLogFilterCollection rootSpecificCollection = filterCollection;
      if (rootSpecificCollection.getStructureFilter() != null) {
        rootSpecificCollection = new VcsLogFilterCollectionBuilder(filterCollection)
          .with(new VcsLogStructureFilterImpl(ContainerUtil.newHashSet(VcsLogUtil.getFilteredFilesForRoot(root, filterCollection))))
          .build();
      }

      List<TimedVcsCommit> matchingCommits = entry.getValue().getCommitsMatchingFilter(root, rootSpecificCollection, maxCount);
      commits.addAll(ContainerUtil.map(matchingCommits, commit -> new CommitId(commit.getId(), root)));
    }

    return commits;
  }

  @Nullable
  private Set<Integer> getMatchedCommitIndex(@Nullable Collection<CommitId> commits) {
    if (commits == null) {
      return null;
    }

    return ContainerUtil.map2Set(commits, commitId -> myStorage.getCommitIndex(commitId.getHash(), commitId.getRoot()));
  }

  public boolean areFiltersAffectedByIndexing(@NotNull VcsLogFilterCollection filters, @NotNull List<VirtualFile> roots) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();
    if (detailsFilters.isEmpty()) return false;

    Set<VirtualFile> affectedRoots = VcsLogUtil.getAllVisibleRoots(roots, filters);
    boolean needsIndex = !affectedRoots.isEmpty();
    if (needsIndex) {
      LOG.debug(filters + " are affected by indexing of " + affectedRoots);
    }
    return needsIndex;
  }

  protected static class FilterByDetailsResult {
    @Nullable public final Set<Integer> matchingCommits;
    public final boolean canRequestMore;
    @NotNull private final CommitCountStage commitCount;

    protected FilterByDetailsResult(@Nullable Set<Integer> commits, boolean more, @NotNull CommitCountStage count) {
      matchingCommits = commits;
      canRequestMore = more;
      commitCount = count;
    }
  }
}
