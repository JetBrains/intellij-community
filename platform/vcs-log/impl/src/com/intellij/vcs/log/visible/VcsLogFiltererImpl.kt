// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogFilter
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.history.FileHistory
import com.intellij.vcs.log.history.FileHistoryBuilder
import com.intellij.vcs.log.history.FileHistoryData
import com.intellij.vcs.log.history.removeTrivialMerges
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.statistics.filtersToStringPresentation
import com.intellij.vcs.log.statistics.vcsToStringPresentation
import com.intellij.vcs.log.util.GraphOptionsUtil.kindName
import com.intellij.vcs.log.util.IntCollectionUtil
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsLogUtil.FULL_HASH_LENGTH
import com.intellij.vcs.log.visible.filters.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.function.BiConsumer
import java.util.stream.Collectors
import kotlin.math.min

class VcsLogFiltererImpl(private val logProviders: Map<VirtualFile, VcsLogProvider>,
                         internal val storage: VcsLogStorage,
                         private val topCommitsDetailsCache: TopCommitsCache,
                         private val commitDetailsGetter: VcsLogCommitDataCache<out VcsFullCommitDetails>,
                         internal val index: VcsLogIndex) : VcsLogFilterer {

  constructor(logData: VcsLogData) : this(logData.logProviders, logData.storage, logData.topCommitsCache, logData.fullCommitDetailsCache,
                                          logData.index)

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      graphOptions: PermanentGraph.Options,
                      allFilters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val hashFilter = allFilters.get(VcsLogFilterCollection.HASH_FILTER)
    val filters = allFilters.without(VcsLogFilterCollection.HASH_FILTER)

    TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogFilter.getName()).use { span ->
      if (hashFilter != null && !hashFilter.hashes.isEmpty()) { // hashes should be shown, no matter if they match other filters or not
        try {
          val hashFilterResult = applyHashFilter(dataPack, hashFilter, graphOptions, commitCount)
          if (hashFilterResult != null) {
            span.configure(dataPack, hashFilterResult.visiblePack.filters, graphOptions, commitCount, hashFilterResult.filterKind)
            return Pair(hashFilterResult.visiblePack, hashFilterResult.commitCount)
          }
        }
        catch (e: VcsException) {
          span.recordError(e)
          val visiblePack = VisiblePack.ErrorVisiblePack(dataPack, VcsLogFilterObject.collection(hashFilter, hashFilter.toTextFilter()), e)
          return Pair(visiblePack, commitCount)
        }
      }

      val visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.logProviders.keys, filters)

      val matchingHeads: Set<VcsLogCommitStorageIndex>?
      var commitCandidates: IntSet? = null
      var forceFilterByVcs = false

      val rangeFilters = allFilters.get(VcsLogFilterCollection.RANGE_FILTER)
      if (rangeFilters != null) {
        val (commits, heads) = filterByRange(dataPack, filters, visibleRoots, rangeFilters)

        commitCandidates = commits
        matchingHeads = heads
        forceFilterByVcs = commits == null
      }
      else {
        matchingHeads = getMatchingHeads(dataPack.refsModel, visibleRoots, filters)
      }

      val parentFilter = allFilters.get(VcsLogFilterCollection.PARENT_FILTER)
      if (parentFilter != null && !parentFilter.matchesAll) {
        commitCandidates = filterByParent(dataPack, parentFilter, commitCandidates)
        forceFilterByVcs = commitCandidates == null
      }

      try {
        val filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads, commitCandidates, graphOptions, forceFilterByVcs)

        val visibleGraph = createVisibleGraph(dataPack, graphOptions, matchingHeads, filterResult.matchingCommits, filterResult.fileHistoryData)
        val visiblePack = VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters)

        span.configure(dataPack, filters, graphOptions, commitCount, filterResult.filterKind)
        return Pair(visiblePack, filterResult.commitCount)
      }
      catch (e: VcsException) {
        span.recordError(e)
        return Pair(VisiblePack.ErrorVisiblePack(dataPack, filters, e), commitCount)
      }
    }
  }

  private fun collectCommitsReachableFromHeads(dataPack: DataPack, matchingHeads: Set<VcsLogCommitStorageIndex>): IntSet {
    @Suppress("UNCHECKED_CAST") val permanentGraph = dataPack.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex> ?: return IntOpenHashSet()
    val startIds = matchingHeads.map { permanentGraph.permanentCommitsInfo.getNodeId(it) }
    val result = IntOpenHashSet()
    DfsWalk(startIds, permanentGraph.linearGraph).walk(true) { node: Int ->
      result.add(permanentGraph.permanentCommitsInfo.getCommitId(node))
      true
    }
    return result
  }

  private fun createVisibleGraph(dataPack: DataPack,
                                 graphOptions: PermanentGraph.Options,
                                 matchingHeads: Set<VcsLogCommitStorageIndex>?,
                                 matchingCommits: Set<VcsLogCommitStorageIndex>?,
                                 fileHistoryData: FileHistoryData?): VisibleGraph<VcsLogCommitStorageIndex> {
    if (matchingHeads.matchesNothing() || matchingCommits.matchesNothing()) {
      return EmptyVisibleGraph.getInstance()
    }

    val permanentGraph = dataPack.permanentGraph
    if (permanentGraph !is PermanentGraphImpl || fileHistoryData == null) {
      return permanentGraph.createVisibleGraph(graphOptions, matchingHeads, matchingCommits)
    }

    if (fileHistoryData.startPaths.size == 1 && fileHistoryData.startPaths.single().isDirectory) {
      val unmatchedRenames = matchingCommits?.let { fileHistoryData.getCommitsWithRenames().subtract(it) } ?: emptySet()
      val preprocessor = FileHistoryBuilder(null, fileHistoryData.startPaths.single(), fileHistoryData,
                                            FileHistory.EMPTY, unmatchedRenames,
                                            removeTrivialMerges = FileHistoryBuilder.isRemoveTrivialMerges,
                                            refine = FileHistoryBuilder.isRefine)
      return permanentGraph.createVisibleGraph(graphOptions, matchingHeads, matchingCommits?.union(unmatchedRenames), preprocessor)
    }

    val preprocessor = BiConsumer<LinearGraphController, PermanentGraphInfo<VcsLogCommitStorageIndex>> { controller, permanentGraphInfo ->
      if (FileHistoryBuilder.isRemoveTrivialMerges) {
        removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData) { trivialMerges ->
          LOG.debug("Removed ${trivialMerges.size} trivial merges")
        }
      }
    }
    return permanentGraph.createVisibleGraph(graphOptions, matchingHeads, matchingCommits, preprocessor)
  }

  @Throws(VcsException::class)
  internal fun filterByDetails(dataPack: DataPack,
                               filters: VcsLogFilterCollection,
                               commitCount: CommitCountStage,
                               visibleRoots: Collection<VirtualFile>,
                               matchingHeads: Set<VcsLogCommitStorageIndex>?,
                               commitCandidates: IntSet?,
                               graphOptions: PermanentGraph.Options,
                               forceFilterByVcs: Boolean): FilterByDetailsResult {
    val detailsFilters = filters.detailsFilters
    if (!forceFilterByVcs && detailsFilters.isEmpty()) {
      return FilterByDetailsResult(commitCandidates, false, commitCount, FilterKind.Memory)
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

    if (rootsForVcs.isEmpty()) return FilterByDetailsResult(filteredWithIndex, false, commitCount, FilterKind.Index, historyData)

    val filterAllWithVcs = rootsForVcs.containsAll(visibleRoots)
    val filtersForVcs = if (filterAllWithVcs) filters else filters.with(VcsLogFilterObject.fromRoots(rootsForVcs))
    val headsForVcs = if (filterAllWithVcs) matchingHeads else getMatchingHeads(dataPack.refsModel, rootsForVcs, filtersForVcs)
    val filteredWithVcs = filterWithVcs(dataPack.permanentGraph, filtersForVcs, headsForVcs, graphOptions, commitCount, commitCandidates)

    val filteredCommits = union(filteredWithIndex, filteredWithVcs.matchingCommits)
    return FilterByDetailsResult(filteredCommits, filteredWithVcs.canRequestMore, filteredWithVcs.commitCount,
                                 if (filterAllWithVcs) filteredWithVcs.filterKind else FilterKind.Mixed, historyData)
  }

  private fun filterWithIndex(dataGetter: IndexDataGetter,
                              detailsFilters: List<VcsLogDetailsFilter>,
                              commitCandidates: IntSet?): Pair<IntSet?, FileHistoryData?> {
    val structureFilter = detailsFilters.filterIsInstance<VcsLogStructureFilter>().singleOrNull()
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
  private fun filterWithVcs(graph: PermanentGraph<VcsLogCommitStorageIndex>,
                            filters: VcsLogFilterCollection,
                            matchingHeads: Set<VcsLogCommitStorageIndex>?,
                            graphOptions: PermanentGraph.Options,
                            commitCount: CommitCountStage,
                            commitCandidates: IntSet?): FilterByDetailsResult {
    var commitCountToTry = commitCount
    if (commitCountToTry.isInitial) {
      if (filters.get(VcsLogFilterCollection.RANGE_FILTER) == null) { // not filtering in memory by range for simplicity
        val commitsFromMemory = filterDetailsInMemory(graph, filters.detailsFilters, matchingHeads, commitCandidates)
        if (commitsFromMemory.size >= commitCountToTry.count) {
          return FilterByDetailsResult(commitsFromMemory, true, commitCountToTry, FilterKind.Memory)
        }
      }
      commitCountToTry = commitCountToTry.next()
    }

    // Let's not load more commits than can be currently displayed.
    // E.g., for a small data pack commitCountToTry is almost always greater than the number of commits there.
    val numberOfCommitsToLoad = min(graph.allCommits.size, commitCountToTry.count)
    val commitsFromVcs = filterWithVcs(filters, graphOptions, numberOfCommitsToLoad)
    return FilterByDetailsResult(commitsFromVcs, commitsFromVcs.size >= numberOfCommitsToLoad, commitCountToTry, FilterKind.Vcs)
  }

  @Throws(VcsException::class)
  internal fun filterWithVcs(filterCollection: VcsLogFilterCollection, graphOptions: PermanentGraph.Options, maxCount: Int): IntSet {
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

      provider.getCommitsMatchingFilter(root, actualFilterCollection, graphOptions, maxCount).forEach { commit ->
        commits.add(storage.getCommitIndex(commit.id, root))
      }
    }

    return commits
  }

  @Throws(VcsException::class)
  private fun applyHashFilter(dataPack: DataPack,
                              hashFilter: VcsLogHashFilter,
                              graphOptions: PermanentGraph.Options,
                              commitCount: CommitCountStage): FilterByHashResult? {
    val hashFilterResult = IntOpenHashSet()
    for (partOfHash in hashFilter.hashes) {
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
    if (!filterMessages || commitCount.isInitial) {
      if (hashFilterResult.isEmpty()) return null

      val visibleGraph = dataPack.permanentGraph.createVisibleGraph(graphOptions, null, hashFilterResult)
      val visiblePack = VisiblePack(dataPack, visibleGraph, filterMessages, VcsLogFilterObject.collection(hashFilter))
      return FilterByHashResult(visiblePack, if (filterMessages) commitCount.next() else commitCount.last(), FilterKind.Memory)
    }

    val textFilter = hashFilter.toTextFilter()
    val textFilterResult = filterByDetails(dataPack, VcsLogFilterObject.collection(textFilter),
                                           commitCount, dataPack.logProviders.keys, null, null, graphOptions, false)
    if (hashFilterResult.isEmpty() && textFilterResult.matchingCommits.matchesNothing()) return null
    val filterResult = union(textFilterResult.matchingCommits, hashFilterResult)

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(graphOptions, null, filterResult)
    val visiblePack = VisiblePack(dataPack, visibleGraph, textFilterResult.canRequestMore,
                                  VcsLogFilterObject.collection(hashFilter, textFilter))
    return FilterByHashResult(visiblePack, textFilterResult.commitCount, textFilterResult.filterKind)
  }

  private fun VcsLogHashFilter.toTextFilter(): VcsLogTextFilter {
    return VcsLogFilterObject.fromPatternsList(ArrayList(hashes), false)
  }

  private fun filterByRange(dataPack: DataPack, filters: VcsLogFilterCollection, visibleRoots: Set<VirtualFile>,
                            rangeFilter: VcsLogRangeFilter): Pair<IntSet?, Set<VcsLogCommitStorageIndex>> {
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

    val commits = when (val commitsForRangeFilter = filterByRange(storage, logProviders, dataPack, rangeFilter)) {
      is RangeFilterResult.Commits -> IntCollectionUtil.union(listOf(commitsReachableFromHeads, commitsForRangeFilter.commits))
      RangeFilterResult.Error, RangeFilterResult.InvalidRange -> null
    }

    /*
      At the same time, the root filter should intersect with the range filter (and the branch filter),
      therefore we take matching heads from the root filter, but use reachable commits set for the branch filter.
    */
    val heads = getMatchingHeads(dataPack.refsModel, visibleRoots)
    return Pair(commits, heads)
  }

  private fun filterByParent(dataPack: DataPack, parentFilter: VcsLogParentFilter, commitCandidates: IntSet?): IntSet? {
    val result = IntOpenHashSet()
    @Suppress("UNCHECKED_CAST") val permanentGraph = dataPack.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex> ?: return null
    if (commitCandidates != null) {
      commitCandidates.forEach(IntConsumer { commit ->
        val nodeId = permanentGraph.permanentCommitsInfo.getNodeId(commit)
        if (matches(permanentGraph.linearGraph, nodeId, parentFilter)) {
          result.add(commit)
        }
      })
    }
    else {
      for (nodeId in 0 until permanentGraph.linearGraph.nodesCount()) {
        if (matches(permanentGraph.linearGraph, nodeId, parentFilter)) {
          result.add(permanentGraph.permanentCommitsInfo.getCommitId(nodeId))
        }
      }
    }
    return result
  }

  fun getMatchingHeads(refs: RefsModel,
                       roots: Collection<VirtualFile>,
                       filters: VcsLogFilterCollection): Set<VcsLogCommitStorageIndex>? {
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
                               revisionFilter: VcsLogRevisionFilter?): Set<VcsLogCommitStorageIndex>? {
    if (branchFilter == null && revisionFilter == null) return null
    val branchMatchingHeads = if (branchFilter != null) getMatchingHeads(refs, roots, branchFilter) else emptySet()
    val revisionMatchingHeads = if (revisionFilter != null) getMatchingHeads(roots, revisionFilter) else emptySet()
    return branchMatchingHeads.union(revisionMatchingHeads)
  }

  private fun getMatchingHeads(refsModel: RefsModel, roots: Collection<VirtualFile>, filter: VcsLogBranchFilter): Set<VcsLogCommitStorageIndex> {
    return mapRefsForRoots(refsModel, roots) { refs ->
      refs.streamBranches().filter { filter.matches(it.name) }.collect(Collectors.toList())
    }.toReferencedCommitIndexes()
  }

  private fun getMatchingHeads(roots: Collection<VirtualFile>, filter: VcsLogRevisionFilter): Set<VcsLogCommitStorageIndex> {
    return filter.heads.filter { roots.contains(it.root) }.mapTo(IntOpenHashSet()) { commitId ->
      storage.getCommitIndex(commitId.hash, commitId.root)
    }
  }

  private fun getMatchingHeads(refsModel: RefsModel, roots: Collection<VirtualFile>): Set<VcsLogCommitStorageIndex> {
    return mapRefsForRoots(refsModel, roots) { refs -> refs.commits }
  }

  private fun <T> mapRefsForRoots(refsModel: RefsModel, roots: Collection<VirtualFile>, mapping: (CompressedRefs) -> Iterable<T>) =
    refsModel.allRefsByRoot.filterKeys { roots.contains(it) }.values.flatMapTo(mutableSetOf(), mapping)

  private fun filterDetailsInMemory(permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
                                    detailsFilters: List<VcsLogDetailsFilter>,
                                    matchingHeads: Set<VcsLogCommitStorageIndex>?,
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
                                permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
                                detailsFilters: List<VcsLogDetailsFilter>,
                                matchingHeads: Set<VcsLogCommitStorageIndex>?): Boolean {
    val matchesAllDetails = detailsFilters.all { filter -> filter.matches(commit) }
    return matchesAllDetails && matchesAnyHead(permanentGraph, commit, matchingHeads)
  }

  private fun matchesAnyHead(permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
                             commit: VcsCommitMetadata,
                             matchingHeads: Set<VcsLogCommitStorageIndex>?): Boolean {
    if (matchingHeads == null) {
      return true
    }
    // TODO O(n^2)
    val commitIndex = storage.getCommitIndex(commit.id, commit.root)
    return ContainerUtil.intersects(permanentGraph.getContainingBranches(commitIndex), matchingHeads)
  }

  private fun getDetailsFromCache(commitIndex: VcsLogCommitStorageIndex): VcsCommitMetadata? {
    return topCommitsDetailsCache.get(commitIndex) ?: commitDetailsGetter.getCachedData(commitIndex)
  }

  private fun Collection<VcsRef>.toReferencedCommitIndexes(): Set<VcsLogCommitStorageIndex> {
    return this.mapTo(mutableSetOf()) { ref ->
      storage.getCommitIndex(ref.commitHash, ref.root)
    }
  }

  private fun Span.configure(dataPack: DataPack,
                             filters: VcsLogFilterCollection,
                             graphOptions: PermanentGraph.Options,
                             commitCount: CommitCountStage,
                             filterKind: FilterKind) {
    if (filterKind == FilterKind.Vcs || filterKind == FilterKind.Mixed) {
      setAttribute(VcsTelemetrySpanAttribute.VCS_LIST.key,
                   logProviders.values.toSet().map { it.supportedVcs }.vcsToStringPresentation())
    }
    setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_FILTERS_LIST.key, filters.keysToSet.filtersToStringPresentation())
    setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_GRAPH_OPTIONS_TYPE.key, graphOptions.kindName)
    if (graphOptions is PermanentGraph.Options.Base) {
      setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_SORT_TYPE.key, graphOptions.sortType.presentation)
    }
    setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_FILTERED_COMMIT_COUNT.key, commitCount.toString())
    if (dataPack.isFull) {
      setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_REPOSITORY_COMMIT_COUNT, dataPack.permanentGraph.allCommits.size)
    }
    setAttribute(VcsTelemetrySpanAttribute.VCS_LOG_FILTER_KIND, filterKind.name)
  }
}

private val LOG = Logger.getInstance(VcsLogFiltererImpl::class.java)

internal enum class FilterKind {
  Vcs, Index, Mixed, Memory
}

internal data class FilterByDetailsResult(val matchingCommits: IntSet?,
                                          val canRequestMore: Boolean,
                                          val commitCount: CommitCountStage,
                                          val filterKind: FilterKind,
                                          val fileHistoryData: FileHistoryData? = null)

private data class FilterByHashResult(val visiblePack: VisiblePack, val commitCount: CommitCountStage, val filterKind: FilterKind)

internal fun areFiltersAffectedByIndexing(filters: VcsLogFilterCollection, roots: List<VirtualFile>): Boolean {
  val detailsFilters = filters.detailsFilters
  if (detailsFilters.isEmpty()) return false

  val affectedRoots = VcsLogUtil.getAllVisibleRoots(roots, filters)
  val needsIndex = affectedRoots.isNotEmpty()
  if (needsIndex) {
    LOG.debug("$filters are affected by indexing of $affectedRoots")
  }
  return needsIndex
}

private fun matches(linearGraph: LinearGraph, node: Int, filter: VcsLogParentFilter): Boolean {
  val parentsCount = linearGraph.getAdjacentEdges(node, EdgeFilter.NORMAL_DOWN).size
  return parentsCount in filter.minParents..filter.maxParents
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

internal fun Span.recordError(e: Exception) {
  recordException(e)
  setStatus(StatusCode.ERROR)
}