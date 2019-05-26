// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.history.EMPTY_HISTORY
import com.intellij.vcs.log.history.FileHistoryBuilder
import com.intellij.vcs.log.history.FileHistoryData
import com.intellij.vcs.log.history.removeTrivialMerges
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.*
import com.intellij.vcs.log.util.VcsLogUtil.FULL_HASH_LENGTH
import com.intellij.vcs.log.util.VcsLogUtil.SHORT_HASH_LENGTH
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromHashes
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import gnu.trove.TIntHashSet
import java.util.function.BiConsumer
import java.util.stream.Collectors

class VcsLogFiltererImpl(private val logProviders: Map<VirtualFile, VcsLogProvider>,
                         private val storage: VcsLogStorage,
                         private val topCommitsDetailsCache: TopCommitsCache,
                         private val commitDetailsGetter: DataGetter<out VcsFullCommitDetails>,
                         private val index: VcsLogIndex) : VcsLogFilterer {

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean = false

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      sortType: PermanentGraph.SortType,
                      allFilters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val hashFilter = allFilters.get(VcsLogFilterCollection.HASH_FILTER)
    val filters = allFilters.without(VcsLogFilterCollection.HASH_FILTER)

    val start = System.currentTimeMillis()

    if (hashFilter != null && !hashFilter.hashes.isEmpty()) { // hashes should be shown, no matter if they match other filters or not
      val hashFilterResult = applyHashFilter(dataPack, hashFilter.hashes, sortType, commitCount)
      if (hashFilterResult != null) {
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                  " for filtering by " + hashFilterResult.first.filters)
        return hashFilterResult
      }
    }

    val visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.logProviders.keys, filters)
    var matchingHeads = getMatchingHeads(dataPack.refsModel, visibleRoots, filters)

    val rangeFilters = allFilters.get(VcsLogFilterCollection.RANGE_FILTER)
    val commitCandidates: TIntHashSet?
    val forceFilterByVcs: Boolean
    if (rangeFilters != null) {
      /*
        If we have both a range filter and a branch filter (e.g. `183\nmaster..feature`) they should be united: the graph should show both
        commits contained in the range, and commits reachable from branches.

        But the main filtering logic is opposite: matchingHeads + some other filter => makes the intersection of commits.
        To overcome this logic for the range filter case, we are not using matchingHeads, but are collecting all commits reachable from
        matchingHeads, and unite them with commits belonging to the range.
       */
      val branchFilter = filters.get(VcsLogFilterCollection.BRANCH_FILTER)
      val revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER)
      val explicitMatchingHeads = getMatchingHeads(dataPack.refsModel, visibleRoots, branchFilter, revisionFilter)
      val commitsReachableFromHeads = if (explicitMatchingHeads != null)
        collectCommitsReachableFromHeads(dataPack, explicitMatchingHeads)
      else TIntHashSet()

      when (val commitsForRangeFilter = filterByRange(dataPack, rangeFilters)) {
        is RangeFilterResult.Commits -> {
          commitCandidates = TroveUtil.union(listOf(commitsReachableFromHeads, commitsForRangeFilter.commits))
          forceFilterByVcs = false
        }
        is RangeFilterResult.Error -> {
          commitCandidates = null
          forceFilterByVcs = true
        }
        is RangeFilterResult.InvalidRange -> {
          return Pair(VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false, filters), CommitCountStage.ALL)
        }
      }

      /*
        At the same time, the root filter should intersect with the range filter (and the branch filter),
        therefore we take matching heads from the root filter, but use reachable commits set for the branch filter.
      */
      val matchingHeadsFromRoots = getMatchingHeads(dataPack.refsModel, visibleRoots)
      matchingHeads = matchingHeadsFromRoots
    }
    else {
      commitCandidates = null
      forceFilterByVcs = false
    }

    val filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads, commitCandidates, forceFilterByVcs)

    val visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits, filterResult.fileHistoryData)
    val visiblePack = VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters)

    LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for filtering by " + filters)
    return Pair(visiblePack, filterResult.commitCount)
  }

  private fun collectCommitsReachableFromHeads(dataPack: DataPack, matchingHeads: Set<Int>): TIntHashSet {
    @Suppress("UNCHECKED_CAST") val permanentGraph = dataPack.permanentGraph as? PermanentGraphInfo<Int> ?: return TIntHashSet()
    val startIds = matchingHeads.map { permanentGraph.permanentCommitsInfo.getNodeId(it) }
    val result = TIntHashSet()
    DfsWalk(startIds, permanentGraph.linearGraph).walk(true) { node: Int ->
      result.add(permanentGraph.permanentCommitsInfo.getCommitId(node))
      true
    }
    return result
  }

  fun createVisibleGraph(dataPack: DataPack,
                         sortType: PermanentGraph.SortType,
                         matchingHeads: Set<Int>?,
                         matchingCommits: Set<Int>?,
                         fileHistoryData: FileHistoryData? = null): VisibleGraph<Int> {
    if (matchingHeads.matchesNothing() || matchingCommits.matchesNothing()) {
      return EmptyVisibleGraph.getInstance()
    }

    val permanentGraph = dataPack.permanentGraph
    if (permanentGraph !is PermanentGraphImpl || fileHistoryData == null) {
      return permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits)
    }

    if (fileHistoryData.startPaths.size == 1 && fileHistoryData.startPaths.single().isDirectory) {
      val unmatchedRenames = matchingCommits?.let { fileHistoryData.getCommitsWithRenames().subtract(it) } ?: emptySet()
      val preprocessor = FileHistoryBuilder(null, fileHistoryData.startPaths.single(), fileHistoryData,
                                            EMPTY_HISTORY, unmatchedRenames)
      return permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits?.union(unmatchedRenames), preprocessor)
    }

    val preprocessor = BiConsumer<LinearGraphController, PermanentGraphInfo<Int>> { controller, permanentGraphInfo ->
      removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData) { trivialMerges ->
        LOG.debug("Removed ${trivialMerges.size} trivial merges")
      }
    }
    return permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits, preprocessor)
  }

  private fun filterByDetails(dataPack: DataPack,
                              filters: VcsLogFilterCollection,
                              commitCount: CommitCountStage,
                              visibleRoots: Collection<VirtualFile>,
                              matchingHeads: Set<Int>?,
                              commitCandidates: TIntHashSet?,
                              forceFilterByVcs: Boolean): FilterByDetailsResult {
    val detailsFilters = filters.detailsFilters
    if (!forceFilterByVcs && detailsFilters.isEmpty()) {
      val matchingCommits = if (commitCandidates != null) TroveUtil.createJavaSet(commitCandidates) else null
      return FilterByDetailsResult(matchingCommits, false, commitCount)
    }

    val dataGetter = index.dataGetter
    val (rootsForIndex, rootsForVcs) = if (dataGetter != null && dataGetter.canFilter(detailsFilters) && !forceFilterByVcs) {
      visibleRoots.partition { index.isIndexed(it) }
    }
    else {
      Pair(emptyList(), visibleRoots.toList())
    }

    val (filteredWithIndex, historyData) = if (rootsForIndex.isNotEmpty())
      filterWithIndex(dataGetter!!, detailsFilters, commitCandidates)
    else Pair(null, null)

    if (rootsForVcs.isEmpty()) return FilterByDetailsResult(filteredWithIndex, false, commitCount, historyData)
    val filterAllWithVcs = rootsForVcs.containsAll(visibleRoots)
    val filtersForVcs = if (filterAllWithVcs) filters else filters.with(VcsLogFilterObject.fromRoots(rootsForVcs))
    val headsForVcs = if (filterAllWithVcs) matchingHeads else getMatchingHeads(dataPack.refsModel, rootsForVcs, filtersForVcs)
    val filteredWithVcs = filterWithVcs(dataPack.permanentGraph, filtersForVcs, headsForVcs, commitCount, commitCandidates)

    val filteredCommits: Set<Int>? = union(filteredWithIndex, filteredWithVcs.matchingCommits)
    return FilterByDetailsResult(filteredCommits, filteredWithVcs.canRequestMore, filteredWithVcs.commitCount, historyData)
  }

  private sealed class RangeFilterResult {
    class Commits(val commits: TIntHashSet) : RangeFilterResult()
    object InvalidRange : RangeFilterResult()
    object Error : RangeFilterResult()
  }

  private fun filterByRange(dataPack: DataPack, rangeFilter: VcsLogRangeFilter): RangeFilterResult {
    val set = TIntHashSet()
    for (range in rangeFilter.ranges) {
      var rangeResolvedAnywhere = false
      for ((root, _) in logProviders) {
        val resolvedRange = resolveCommits(dataPack, root, range)
        if (resolvedRange != null) {
          val commits = getCommitsByRange(dataPack, root, resolvedRange)
          if (commits == null) return RangeFilterResult.Error // error => will be handled by the VCS provider
          else TroveUtil.addAll(set, commits)
          rangeResolvedAnywhere = true
        }
      }
      // If a range is resolved in some roots, but not all of them => skip others and handle those which know about the range.
      // Otherwise, if none of the roots know about the range => return null and let VcsLogProviders handle the range
      if (!rangeResolvedAnywhere) {
        LOG.warn("Range limits unresolved for: $range")
        return RangeFilterResult.InvalidRange
      }
    }
    return RangeFilterResult.Commits(set)
  }

  private fun resolveCommits(dataPack: DataPack, root: VirtualFile, range: VcsLogRangeFilter.RefRange): Pair<CommitId, CommitId>? {
    val from = resolveCommit(dataPack, root, range.exclusiveRef)
    val to = resolveCommit(dataPack, root, range.inclusiveRef)
    if (from == null || to == null) {
      LOG.debug("Range limits unresolved for: $range in $root")
      return null
    }
    return from to to
  }

  private fun getCommitsByRange(dataPack: DataPack, root: VirtualFile, range: Pair<CommitId, CommitId>): TIntHashSet? {
    val fromIndex = storage.getCommitIndex(range.first.hash, root)
    val toIndex = storage.getCommitIndex(range.second.hash, root)

    return dataPack.subgraphDifference(toIndex, fromIndex)
  }

  private fun resolveCommit(dataPack: DataPack, root: VirtualFile, refName: String): CommitId? {
    if (refName.length == FULL_HASH_LENGTH && VcsLogUtil.HASH_REGEX.matcher(refName).matches()) {
      return CommitId(HashImpl.build(refName), root)
    }

    val ref = dataPack.refsModel.findBranch(refName, root)
    return if (ref != null) {
      CommitId(ref.commitHash, root)
    }
    else if (refName.length >= SHORT_HASH_LENGTH && VcsLogUtil.HASH_REGEX.matcher(refName).matches()) {
      // don't search for too short hashes: high probability to treat a ref, existing not in all roots, as a hash
      storage.findCommitId(CommitIdByStringCondition(refName))
    }
    else null
  }

  private fun filterWithIndex(dataGetter: IndexDataGetter,
                              detailsFilters: List<VcsLogDetailsFilter>,
                              commitCandidates: TIntHashSet?): Pair<Set<Int>?, FileHistoryData?> {
    val structureFilter = detailsFilters.filterIsInstance(VcsLogStructureFilter::class.java).singleOrNull()
                          ?: return Pair(dataGetter.filter(detailsFilters, commitCandidates), null)

    val historyData = dataGetter.createFileHistoryData(structureFilter.files).build()
    val candidates = TroveUtil.intersect(TroveUtil.createTroveSet(historyData.getCommits()), commitCandidates)

    val filtersWithoutStructure = detailsFilters.filterNot { it is VcsLogStructureFilter }
    if (filtersWithoutStructure.isEmpty()) return Pair(TroveUtil.createJavaSet(candidates), historyData)

    return Pair(dataGetter.filter(filtersWithoutStructure, candidates), historyData)
  }

  private fun filterWithVcs(graph: PermanentGraph<Int>,
                            filters: VcsLogFilterCollection,
                            matchingHeads: Set<Int>?,
                            commitCount: CommitCountStage,
                            commitCandidates: TIntHashSet?): FilterByDetailsResult {
    var commitCountToTry = commitCount
    if (commitCountToTry == CommitCountStage.INITIAL) {
      val commitsFromMemory = filterDetailsInMemory(graph, filters.detailsFilters, matchingHeads, commitCandidates).toCommitIndexes()
      if (commitsFromMemory.size >= commitCountToTry.count) {
        return FilterByDetailsResult(commitsFromMemory, true, commitCountToTry)
      }
      commitCountToTry = commitCountToTry.next()
    }

    try {
      val commitsFromVcs = filteredDetailsInVcs(logProviders, filters, commitCountToTry.count).toCommitIndexes()
      return FilterByDetailsResult(commitsFromVcs, commitsFromVcs.size >= commitCountToTry.count, commitCountToTry)
    }
    catch (e: VcsException) {
      //TODO show an error balloon or something else for non-ea guys.
      LOG.error(e)
      return FilterByDetailsResult(emptySet(), true, commitCountToTry)
    }
  }

  @Throws(VcsException::class)
  private fun filteredDetailsInVcs(providers: Map<VirtualFile, VcsLogProvider>,
                                   filterCollection: VcsLogFilterCollection,
                                   maxCount: Int): Collection<CommitId> {
    val commits = mutableListOf<CommitId>()

    val visibleRoots = VcsLogUtil.getAllVisibleRoots(providers.keys, filterCollection)
    for (root in visibleRoots) {

      val userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER)
      if (userFilter != null && userFilter.getUsers(root).isEmpty()) {
        // there is a structure or user filter, but it doesn't match this root
        continue
      }

      val filesForRoot = VcsLogUtil.getFilteredFilesForRoot(root, filterCollection)
      val rootSpecificCollection = if (filesForRoot.isEmpty()) {
        filterCollection.without(VcsLogFilterCollection.STRUCTURE_FILTER)
      }
      else {
        filterCollection.with(VcsLogFilterObject.fromPaths(filesForRoot))
      }

      val matchingCommits = providers.getValue(root).getCommitsMatchingFilter(root, rootSpecificCollection, maxCount)
      commits.addAll(matchingCommits.map { commit -> CommitId(commit.id, root) })
    }

    return commits
  }

  private fun applyHashFilter(dataPack: DataPack,
                              hashes: Collection<String>,
                              sortType: PermanentGraph.SortType,
                              commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage>? {
    val hashFilterResult = hashSetOf<Int>()
    for (partOfHash in hashes) {
      if (partOfHash.length == FULL_HASH_LENGTH) {
        val hash = HashImpl.build(partOfHash)
        for (root in dataPack.logProviders.keys) {
          if (storage.containsCommit(CommitId(hash, root))) {
            hashFilterResult.add(storage.getCommitIndex(hash, root))
          }
        }
      }
      else {
        val commitId = storage.findCommitId(CommitIdByStringCondition(partOfHash))
        if (commitId != null) hashFilterResult.add(storage.getCommitIndex(commitId.hash, commitId.root))
      }
    }
    if (!Registry.`is`("vcs.log.filter.messages.by.hash")) {
      if (hashFilterResult.isEmpty()) return null

      val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, hashFilterResult)
      val visiblePack = VisiblePack(dataPack, visibleGraph, false, VcsLogFilterObject.collection(fromHashes(hashes)))
      return Pair(visiblePack, CommitCountStage.ALL)
    }

    val textFilter = VcsLogFilterObject.fromPatternsList(ArrayList(hashes), false)
    val textFilterResult = filterByDetails(dataPack, VcsLogFilterObject.collection(textFilter),
                                           commitCount, dataPack.logProviders.keys, null, null, false)
    if (hashFilterResult.isEmpty() && textFilterResult.matchingCommits.matchesNothing()) return null
    val filterResult = union(textFilterResult.matchingCommits, hashFilterResult)

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, filterResult)
    val visiblePack = VisiblePack(dataPack, visibleGraph, textFilterResult.canRequestMore,
                                  VcsLogFilterObject.collection(fromHashes(hashes), textFilter))
    return Pair(visiblePack, textFilterResult.commitCount)
  }

  fun getMatchingHeads(refs: RefsModel,
                       roots: Collection<VirtualFile>,
                       filters: VcsLogFilterCollection): Set<Int>? {
    val branchFilter = filters.get(VcsLogFilterCollection.BRANCH_FILTER)
    val revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER)

    if (branchFilter == null &&
        revisionFilter == null &&
        filters.get(VcsLogFilterCollection.ROOT_FILTER) == null &&
        filters.get(VcsLogFilterCollection.STRUCTURE_FILTER) == null) {
      return null
    }

    if (revisionFilter != null) {
      if (branchFilter == null) {
        return getMatchingHeads(roots, revisionFilter)
      }

      return getMatchingHeads(refs, roots, branchFilter).union(getMatchingHeads(roots, revisionFilter))
    }

    if (branchFilter == null) return getMatchingHeads(refs, roots)
    return getMatchingHeads(refs, roots, branchFilter)
  }

  private fun getMatchingHeads(refs: RefsModel,
                               roots: Collection<VirtualFile>,
                               branchFilter: VcsLogBranchFilter?,
                               revisionFilter: VcsLogRevisionFilter?): Set<Int>? {
    if (branchFilter == null && revisionFilter == null) return null
    val branchMatchingHeads = if (branchFilter != null) getMatchingHeads(refs, roots, branchFilter) else emptySet()
    val revisionMatchingHeads = if (revisionFilter != null) getMatchingHeads(roots, revisionFilter) else emptySet()
    return branchMatchingHeads.union(revisionMatchingHeads)
  }

  private fun getMatchingHeads(refsModel: RefsModel, roots: Collection<VirtualFile>, filter: VcsLogBranchFilter): Set<Int> {
    return mapRefsForRoots(refsModel, roots) { refs ->
      refs.streamBranches().filter { filter.matches(it.name) }.collect(Collectors.toList())
    }.toReferencedCommitIndexes()
  }

  private fun getMatchingHeads(roots: Collection<VirtualFile>, filter: VcsLogRevisionFilter): Set<Int> {
    return filter.heads.filter { roots.contains(it.root) }.toCommitIndexes()
  }

  private fun getMatchingHeads(refsModel: RefsModel, roots: Collection<VirtualFile>): Set<Int> {
    return mapRefsForRoots(refsModel, roots) { refs -> refs.commits }
  }

  private fun <T> mapRefsForRoots(refsModel: RefsModel, roots: Collection<VirtualFile>, mapping: (CompressedRefs) -> Iterable<T>) =
    refsModel.allRefsByRoot.filterKeys { roots.contains(it) }.values.flatMapTo(mutableSetOf(), mapping)

  private fun filterDetailsInMemory(permanentGraph: PermanentGraph<Int>,
                                    detailsFilters: List<VcsLogDetailsFilter>,
                                    matchingHeads: Set<Int>?,
                                    commitCandidates: TIntHashSet?): Collection<CommitId> {
    val result = mutableListOf<CommitId>()
    for (commit in permanentGraph.allCommits) {
      if (commitCandidates == null || commitCandidates.contains(commit.id)) {
        val data = getDetailsFromCache(commit.id)
                   ?: // no more continuous details in the cache
                   break
        if (matchesAllFilters(data, permanentGraph, detailsFilters, matchingHeads)) {
          result.add(CommitId(data.id, data.root))
        }
      }
    }
    return result
  }

  private fun matchesAllFilters(commit: VcsCommitMetadata,
                                permanentGraph: PermanentGraph<Int>,
                                detailsFilters: List<VcsLogDetailsFilter>,
                                matchingHeads: Set<Int>?): Boolean {
    val matchesAllDetails = detailsFilters.all { filter -> filter.matches(commit) }
    return matchesAllDetails && matchesAnyHead(permanentGraph, commit, matchingHeads)
  }

  private fun matchesAnyHead(permanentGraph: PermanentGraph<Int>,
                             commit: VcsCommitMetadata,
                             matchingHeads: Set<Int>?): Boolean {
    if (matchingHeads == null) {
      return true
    }
    // TODO O(n^2)
    val commitIndex = storage.getCommitIndex(commit.id, commit.root)
    return ContainerUtil.intersects(permanentGraph.getContainingBranches(commitIndex), matchingHeads)
  }

  private fun getDetailsFromCache(commitIndex: Int): VcsCommitMetadata? {
    return topCommitsDetailsCache.get(commitIndex) ?: UIUtil.invokeAndWaitIfNeeded(
      Computable<VcsCommitMetadata> { commitDetailsGetter.getCommitDataIfAvailable(commitIndex) })
  }

  private fun Collection<CommitId>.toCommitIndexes(): Set<Int> {
    return this.mapTo(mutableSetOf()) { commitId ->
      storage.getCommitIndex(commitId.hash, commitId.root)
    }
  }

  private fun Collection<VcsRef>.toReferencedCommitIndexes(): Set<Int> {
    return this.mapTo(mutableSetOf()) { ref ->
      storage.getCommitIndex(ref.commitHash, ref.root)
    }
  }
}

private val LOG = Logger.getInstance(VcsLogFiltererImpl::class.java)

private data class FilterByDetailsResult(val matchingCommits: Set<Int>?,
                                         val canRequestMore: Boolean,
                                         val commitCount: CommitCountStage,
                                         val fileHistoryData: FileHistoryData? = null)

fun areFiltersAffectedByIndexing(filters: VcsLogFilterCollection, roots: List<VirtualFile>): Boolean {
  val detailsFilters = filters.detailsFilters
  if (detailsFilters.isEmpty()) return false

  val affectedRoots = VcsLogUtil.getAllVisibleRoots(roots, filters)
  val needsIndex = affectedRoots.isNotEmpty()
  if (needsIndex) {
    LOG.debug("$filters are affected by indexing of $affectedRoots")
  }
  return needsIndex
}

internal fun <T> Collection<T>?.matchesNothing(): Boolean {
  return this != null && this.isEmpty()
}

internal fun <T> union(c1: Set<T>?, c2: Set<T>?): Set<T>? {
  if (c1 == null) return c2
  if (c2 == null) return c1
  return c1.union(c2)
}
