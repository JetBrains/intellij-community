// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsScope
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan.LogFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.util.containers.ContainerUtil
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
import com.intellij.vcs.log.history.FileHistory
import com.intellij.vcs.log.history.FileHistoryBuilder
import com.intellij.vcs.log.history.FileHistoryData
import com.intellij.vcs.log.history.removeTrivialMerges
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.*
import com.intellij.vcs.log.util.VcsLogUtil.FULL_HASH_LENGTH
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.function.BiConsumer
import java.util.stream.Collectors

class VcsLogFiltererImpl(private val logProviders: Map<VirtualFile, VcsLogProvider>,
                         internal val storage: VcsLogStorage,
                         private val topCommitsDetailsCache: TopCommitsCache,
                         private val commitDetailsGetter: DataGetter<out VcsFullCommitDetails>,
                         internal val index: VcsLogIndex) : VcsLogFilterer {

  constructor(logData: VcsLogData) : this(logData.logProviders, logData.storage, logData.topCommitsCache, logData.commitDetailsGetter,
                                          logData.index)

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      sortType: PermanentGraph.SortType,
                      allFilters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val hashFilter = allFilters.get(VcsLogFilterCollection.HASH_FILTER)
    val filters = allFilters.without(VcsLogFilterCollection.HASH_FILTER)

    TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogFilter.getName()).useWithScope {
      if (hashFilter != null && !hashFilter.hashes.isEmpty()) { // hashes should be shown, no matter if they match other filters or not
        val hashFilterResult = applyHashFilter(dataPack, hashFilter, sortType, commitCount)
        if (hashFilterResult != null) {
          it.setAttribute("filters", hashFilterResult.first.filters.toString())
          it.setAttribute("sortType", sortType.toString())
          return hashFilterResult
        }
      }

      val visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.logProviders.keys, filters)
      var matchingHeads = getMatchingHeads(dataPack.refsModel, visibleRoots, filters)

      val rangeFilters = allFilters.get(VcsLogFilterCollection.RANGE_FILTER)
      val commitCandidates: IntSet?
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
        else IntOpenHashSet()

        when (val commitsForRangeFilter = filterByRange(storage, logProviders, dataPack, rangeFilters)) {
          is RangeFilterResult.Commits -> {
            commitCandidates = IntCollectionUtil.union(listOf(commitsReachableFromHeads, commitsForRangeFilter.commits))
            forceFilterByVcs = false
          }
          is RangeFilterResult.Error -> {
            commitCandidates = null
            forceFilterByVcs = true
          }
          is RangeFilterResult.InvalidRange -> {
            commitCandidates = null
            forceFilterByVcs = true
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

      try {
        val filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads, commitCandidates, forceFilterByVcs)

        val visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits, filterResult.fileHistoryData)
        val visiblePack = VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters)

        it.setAttribute("filters", filters.toString())
        it.setAttribute("sortType", sortType.toString())
        return Pair(visiblePack, filterResult.commitCount)
      }
      catch (e: VcsException) {
        return Pair(VisiblePack.ErrorVisiblePack(dataPack, filters, e), commitCount)
      }
    }
  }

  private fun collectCommitsReachableFromHeads(dataPack: DataPack, matchingHeads: Set<Int>): IntSet {
    @Suppress("UNCHECKED_CAST") val permanentGraph = dataPack.permanentGraph as? PermanentGraphInfo<Int> ?: return IntOpenHashSet()
    val startIds = matchingHeads.map { permanentGraph.permanentCommitsInfo.getNodeId(it) }
    val result = IntOpenHashSet()
    DfsWalk(startIds, permanentGraph.linearGraph).walk(true) { node: Int ->
      result.add(permanentGraph.permanentCommitsInfo.getCommitId(node))
      true
    }
    return result
  }

  private fun createVisibleGraph(dataPack: DataPack,
                                 sortType: PermanentGraph.SortType,
                                 matchingHeads: Set<Int>?,
                                 matchingCommits: Set<Int>?,
                                 fileHistoryData: FileHistoryData?): VisibleGraph<Int> {
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
                                            FileHistory.EMPTY, unmatchedRenames,
                                            removeTrivialMerges = FileHistoryBuilder.isRemoveTrivialMerges,
                                            refine = FileHistoryBuilder.isRefine)
      return permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits?.union(unmatchedRenames), preprocessor)
    }

    val preprocessor = BiConsumer<LinearGraphController, PermanentGraphInfo<Int>> { controller, permanentGraphInfo ->
      if (FileHistoryBuilder.isRemoveTrivialMerges) {
        removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData) { trivialMerges ->
          LOG.debug("Removed ${trivialMerges.size} trivial merges")
        }
      }
    }
    return permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits, preprocessor)
  }

  @Throws(VcsException::class)
  internal fun filterByDetails(dataPack: DataPack,
                               filters: VcsLogFilterCollection,
                               commitCount: CommitCountStage,
                               visibleRoots: Collection<VirtualFile>,
                               matchingHeads: Set<Int>?,
                               commitCandidates: IntSet?,
                               forceFilterByVcs: Boolean): FilterByDetailsResult {
    val detailsFilters = filters.detailsFilters
    if (!forceFilterByVcs && detailsFilters.isEmpty()) {
      return FilterByDetailsResult(commitCandidates, false, commitCount)
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

    val filteredCommits = union(filteredWithIndex, filteredWithVcs.matchingCommits)
    return FilterByDetailsResult(filteredCommits, filteredWithVcs.canRequestMore, filteredWithVcs.commitCount, historyData)
  }

  private fun filterWithIndex(dataGetter: IndexDataGetter,
                              detailsFilters: List<VcsLogDetailsFilter>,
                              commitCandidates: IntSet?): Pair<IntSet?, FileHistoryData?> {
    val structureFilter = detailsFilters.filterIsInstance(VcsLogStructureFilter::class.java).singleOrNull()
                          ?: return Pair(dataGetter.filter(detailsFilters, commitCandidates), null)

    val historyData = dataGetter.createFileHistoryData(structureFilter.files).build()
    val candidates = IntCollectionUtil.intersect(historyData.getCommits(), commitCandidates)

    val filtersWithoutStructure = detailsFilters.filterNot { it is VcsLogStructureFilter }
    if (filtersWithoutStructure.isEmpty()) {
      return Pair(candidates, historyData)
    }

    return Pair(dataGetter.filter(filtersWithoutStructure, candidates), historyData)
  }

  @Throws(VcsException::class)
  private fun filterWithVcs(graph: PermanentGraph<Int>,
                            filters: VcsLogFilterCollection,
                            matchingHeads: Set<Int>?,
                            commitCount: CommitCountStage,
                            commitCandidates: IntSet?): FilterByDetailsResult {
    var commitCountToTry = commitCount
    if (commitCountToTry == CommitCountStage.INITIAL) {
      if (filters.get(VcsLogFilterCollection.RANGE_FILTER) == null) { // not filtering in memory by range for simplicity
        val commitsFromMemory = filterDetailsInMemory(graph, filters.detailsFilters, matchingHeads, commitCandidates)
        if (commitsFromMemory.size >= commitCountToTry.count) {
          return FilterByDetailsResult(commitsFromMemory, true, commitCountToTry)
        }
      }
      commitCountToTry = commitCountToTry.next()
    }

    val commitsFromVcs = filterWithVcs(filters, commitCountToTry.count)
    return FilterByDetailsResult(commitsFromVcs, commitsFromVcs.size >= commitCountToTry.count, commitCountToTry)
  }

  @Throws(VcsException::class)
  internal fun filterWithVcs(filterCollection: VcsLogFilterCollection, maxCount: Int): IntSet {
    val commits = IntOpenHashSet()

    val visibleRoots = VcsLogUtil.getAllVisibleRoots(logProviders.keys, filterCollection)
    for (root in visibleRoots) {
      val provider = logProviders.getValue(root)

      val userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER)
      if (userFilter != null && userFilter.getUsers(root).isEmpty()) {
        // there is a structure or user filter, but it doesn't match this root
        continue
      }

      val filesForRoot = VcsLogUtil.getFilteredFilesForRoot(root, filterCollection)
      var actualFilterCollection = if (filesForRoot.isEmpty()) {
        filterCollection.without(VcsLogFilterCollection.STRUCTURE_FILTER)
      }
      else {
        filterCollection.with(VcsLogFilterObject.fromPaths(filesForRoot))
      }

      val rangeFilter = filterCollection.get(VcsLogFilterCollection.RANGE_FILTER)
      if (rangeFilter != null) {
        val resolvedRanges = mutableListOf<VcsLogRangeFilter.RefRange>()
        for ((exclusiveRef, inclusiveRef) in rangeFilter.ranges) {
          val exclusiveHash = provider.resolveReference(exclusiveRef, root)
          val inclusiveHash = provider.resolveReference(inclusiveRef, root)

          if (exclusiveHash != null && inclusiveHash != null) {
            resolvedRanges.add(VcsLogRangeFilter.RefRange(exclusiveHash.asString(), inclusiveHash.asString()))
          }
        }

        if (resolvedRanges.isEmpty()) {
          continue
        }

        actualFilterCollection = filterCollection
          .without(VcsLogFilterCollection.RANGE_FILTER)
          .with(VcsLogFilterObject.fromRange(resolvedRanges))
      }

      provider.getCommitsMatchingFilter(root, actualFilterCollection, maxCount).forEach { commit ->
        commits.add(storage.getCommitIndex(commit.id, root))
      }
    }

    return commits
  }

  private fun applyHashFilter(dataPack: DataPack,
                              hashFilter: VcsLogHashFilter,
                              sortType: PermanentGraph.SortType,
                              commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage>? {
    val hashes = hashFilter.hashes
    val hashFilterResult = IntOpenHashSet()
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
    val filterMessages = Registry.`is`("vcs.log.filter.messages.by.hash")
    if (!filterMessages || commitCount == CommitCountStage.INITIAL) {
      if (hashFilterResult.isEmpty()) return null

      val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, hashFilterResult)
      val visiblePack = VisiblePack(dataPack, visibleGraph, filterMessages, VcsLogFilterObject.collection(hashFilter))
      return Pair(visiblePack, if (filterMessages) commitCount.next() else CommitCountStage.ALL)
    }

    val textFilter = VcsLogFilterObject.fromPatternsList(ArrayList(hashes), false)
    try {
      val textFilterResult = filterByDetails(dataPack, VcsLogFilterObject.collection(textFilter),
                                             commitCount, dataPack.logProviders.keys, null, null, false)
      if (hashFilterResult.isEmpty() && textFilterResult.matchingCommits.matchesNothing()) return null
      val filterResult = union(textFilterResult.matchingCommits, hashFilterResult)

      val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, filterResult)
      val visiblePack = VisiblePack(dataPack, visibleGraph, textFilterResult.canRequestMore,
                                    VcsLogFilterObject.collection(hashFilter, textFilter))
      return Pair(visiblePack, textFilterResult.commitCount)
    }
    catch (e: VcsException) {
      return Pair(VisiblePack.ErrorVisiblePack(dataPack, VcsLogFilterObject.collection(hashFilter, textFilter), e), commitCount)
    }
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
    return filter.heads.filter { roots.contains(it.root) }.mapTo(IntOpenHashSet()) { commitId ->
      storage.getCommitIndex(commitId.hash, commitId.root)
    }
  }

  private fun getMatchingHeads(refsModel: RefsModel, roots: Collection<VirtualFile>): Set<Int> {
    return mapRefsForRoots(refsModel, roots) { refs -> refs.commits }
  }

  private fun <T> mapRefsForRoots(refsModel: RefsModel, roots: Collection<VirtualFile>, mapping: (CompressedRefs) -> Iterable<T>) =
    refsModel.allRefsByRoot.filterKeys { roots.contains(it) }.values.flatMapTo(mutableSetOf(), mapping)

  private fun filterDetailsInMemory(permanentGraph: PermanentGraph<Int>,
                                    detailsFilters: List<VcsLogDetailsFilter>,
                                    matchingHeads: Set<Int>?,
                                    commitCandidates: IntSet?): IntSet {
    val result = IntOpenHashSet()
    for (commit in permanentGraph.allCommits) {
      if (commitCandidates == null || commitCandidates.contains(commit.id)) {
        val data = getDetailsFromCache(commit.id)
                   ?: // no more continuous details in the cache
                   break
        if (matchesAllFilters(data, permanentGraph, detailsFilters, matchingHeads)) {
          result.add(storage.getCommitIndex(data.id, data.root))
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
    return topCommitsDetailsCache.get(commitIndex) ?: commitDetailsGetter.getCommitDataIfAvailable(commitIndex)
  }

  private fun Collection<VcsRef>.toReferencedCommitIndexes(): Set<Int> {
    return this.mapTo(mutableSetOf()) { ref ->
      storage.getCommitIndex(ref.commitHash, ref.root)
    }
  }
}

private val LOG = Logger.getInstance(VcsLogFiltererImpl::class.java)

internal data class FilterByDetailsResult(val matchingCommits: IntSet?,
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

internal fun union(c1: IntSet?, c2: IntSet?): IntSet? {
  if (c1 == null) return c2
  if (c2 == null) return c1

  val result = IntOpenHashSet(c1)
  result.addAll(c2)
  return result
}
