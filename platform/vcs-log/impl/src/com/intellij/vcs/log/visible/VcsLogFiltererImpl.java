// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromHashes;

public class VcsLogFiltererImpl implements VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(VcsLogFiltererImpl.class);

  @NotNull protected final VcsLogStorage myStorage;
  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;
  @NotNull private final DataGetter<? extends VcsFullCommitDetails> myCommitDetailsGetter;
  @NotNull protected final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull protected final VcsLogIndex myIndex;

  public VcsLogFiltererImpl(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                            @NotNull VcsLogStorage storage,
                            @NotNull TopCommitsCache topCommitsDetailsCache,
                            @NotNull DataGetter<? extends VcsFullCommitDetails> detailsGetter,
                            @NotNull VcsLogIndex index) {
    myStorage = storage;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myCommitDetailsGetter = detailsGetter;
    myLogProviders = providers;
    myIndex = index;
  }

  @Override
  @NotNull
  public Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack dataPack,
                                                    @NotNull PermanentGraph.SortType sortType,
                                                    @NotNull VcsLogFilterCollection filters,
                                                    @NotNull CommitCountStage commitCount) {
    long start = System.currentTimeMillis();

    VcsLogHashFilter hashFilter = filters.get(VcsLogFilterCollection.HASH_FILTER);
    if (hashFilter != null && !hashFilter.getHashes().isEmpty()) { // hashes should be shown, no matter if they match other filters or not
      Pair<VisiblePack, CommitCountStage> hashFilterResult = applyHashFilter(dataPack, hashFilter.getHashes(), sortType, commitCount);
      if (hashFilterResult != null) {
        LOG.debug(
          StopWatch.formatTime(System.currentTimeMillis() - start) + " for filtering by " + hashFilterResult.getFirst().getFilters());
        return hashFilterResult;
      }
    }

    filters = VcsLogFiltersKt.without(filters, VcsLogFilterCollection.HASH_FILTER);

    Collection<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.getLogProviders().keySet(), filters);
    Set<Integer> matchingHeads = getMatchingHeads(dataPack.getRefsModel(), visibleRoots, filters);
    FilterByDetailsResult filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads);

    VisibleGraph<Integer> visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits);
    VisiblePack visiblePack = new VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters);

    LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for filtering by " + filters);
    return Pair.create(visiblePack, filterResult.commitCount);
  }

  @NotNull
  public VisibleGraph<Integer> createVisibleGraph(@NotNull DataPack dataPack,
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
    IndexDataGetter dataGetter = myIndex.getDataGetter();
    if (dataGetter != null && dataGetter.canFilter(detailsFilters)) {
      Collection<VirtualFile> notIndexedRoots = ContainerUtil.filter(visibleRoots, root -> !myIndex.isIndexed(root));

      if (notIndexedRoots.size() < visibleRoots.size()) {
        filteredWidthIndex = dataGetter.filter(detailsFilters);
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

  @Override
  public boolean canFilterEmptyPack(@NotNull VcsLogFilterCollection filters) {
    return false;
  }

  public static <T> boolean matchesNothing(@Nullable Collection<T> matchingSet) {
    return matchingSet != null && matchingSet.isEmpty();
  }

  @Nullable
  private Pair<VisiblePack, CommitCountStage> applyHashFilter(@NotNull DataPack dataPack,
                                                              @NotNull Collection<String> hashes,
                                                              @NotNull PermanentGraph.SortType sortType,
                                                              @NotNull CommitCountStage commitCount) {
    Set<Integer> hashFilterResult = ContainerUtil.newHashSet();
    for (String partOfHash : hashes) {
      if (partOfHash.length() == VcsLogUtil.FULL_HASH_LENGTH) {
        Hash hash = HashImpl.build(partOfHash);
        for (VirtualFile root : dataPack.getLogProviders().keySet()) {
          if (myStorage.containsCommit(new CommitId(hash, root))) {
            hashFilterResult.add(myStorage.getCommitIndex(hash, root));
          }
        }
      }
      else {
        CommitId commitId = myStorage.findCommitId(new CommitIdByStringCondition(partOfHash));
        if (commitId != null) hashFilterResult.add(myStorage.getCommitIndex(commitId.getHash(), commitId.getRoot()));
      }
    }
    VcsLogTextFilter textFilter = VcsLogFilterObject.fromPatternsList(ContainerUtil.newArrayList(hashes), false);
    FilterByDetailsResult textFilterResult = filterByDetails(dataPack, VcsLogFilterObject.collection(textFilter),
                                                             commitCount, dataPack.getLogProviders().keySet(), null);
    if (hashFilterResult.isEmpty() && matchesNothing(textFilterResult.matchingCommits)) return null;
    Set<Integer> filterResult = textFilterResult.matchingCommits == null ? hashFilterResult :
                                ContainerUtil.union(hashFilterResult, textFilterResult.matchingCommits);

    VisibleGraph<Integer> visibleGraph = dataPack.getPermanentGraph().createVisibleGraph(sortType, null, filterResult);
    VisiblePack visiblePack = new VisiblePack(dataPack, visibleGraph, textFilterResult.canRequestMore,
                                              VcsLogFilterObject.collection(fromHashes(hashes), textFilter));
    return Pair.create(visiblePack, textFilterResult.commitCount);
  }

  @Nullable
  public Set<Integer> getMatchingHeads(@NotNull VcsLogRefs refs,
                                       @NotNull Collection<VirtualFile> roots,
                                       @NotNull VcsLogFilterCollection filters) {
    VcsLogBranchFilter branchFilter = filters.get(VcsLogFilterCollection.BRANCH_FILTER);
    VcsLogRevisionFilter revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER);

    if (branchFilter == null &&
        revisionFilter == null &&
        filters.get(VcsLogFilterCollection.ROOT_FILTER) == null &&
        filters.get(VcsLogFilterCollection.STRUCTURE_FILTER) == null) {
      return null;
    }

    if (revisionFilter != null) {
      if (branchFilter == null) {
        return getMatchingHeads(roots, revisionFilter);
      }

      Set<Integer> filteredByFile = getMatchingHeads(refs, roots);
      Set<Integer> filteredByBranch = getMatchingHeads(refs, branchFilter);
      return new HashSet<>(ContainerUtil.union(ContainerUtil.intersection(filteredByBranch, filteredByFile),
                                               getMatchingHeads(roots, revisionFilter)));
    }

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
  private Set<Integer> getMatchingHeads(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogRevisionFilter filter) {
    return ContainerUtil.map2SetNotNull(filter.getHeads(), commit -> {
      if (roots.contains(commit.getRoot())) {
        return myStorage.getCommitIndex(commit.getHash(), commit.getRoot());
      }
      return null;
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

      VcsLogUserFilter userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER);
      if (!visibleRoots.contains(root) ||
          (userFilter != null && userFilter.getUsers(root).isEmpty())) {
        // there is a structure or user filter, but it doesn't match this root
        continue;
      }

      Set<FilePath> filesForRoot = VcsLogUtil.getFilteredFilesForRoot(root, filterCollection);
      VcsLogFilterCollection rootSpecificCollection;
      if (filesForRoot.isEmpty()) {
        rootSpecificCollection = VcsLogFiltersKt.without(filterCollection, VcsLogFilterCollection.STRUCTURE_FILTER);
      }
      else {
        rootSpecificCollection = VcsLogFiltersKt.with(filterCollection, VcsLogFilterObject.fromPaths(filesForRoot));
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

  public static boolean areFiltersAffectedByIndexing(@NotNull VcsLogFilterCollection filters, @NotNull List<VirtualFile> roots) {
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
