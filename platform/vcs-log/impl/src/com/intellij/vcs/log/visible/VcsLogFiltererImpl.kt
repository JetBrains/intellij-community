// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.StopWatch
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromHashes
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import java.util.*

class VcsLogFiltererImpl(private val logProviders: Map<VirtualFile, VcsLogProvider>,
                         private val storage: VcsLogStorage,
                         private val topCommitsDetailsCache: TopCommitsCache,
                         private val commitDetailsGetter: DataGetter<out VcsFullCommitDetails>,
                         private val index: VcsLogIndex) : VcsLogFilterer {

  override fun filter(dataPack: DataPack,
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
    val matchingHeads = getMatchingHeads(dataPack.refsModel, visibleRoots, filters)
    val filterResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads)

    val visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits)
    val visiblePack = VisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters)

    LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for filtering by " + filters)
    return Pair.create(visiblePack, filterResult.commitCount)
  }

  fun createVisibleGraph(dataPack: DataPack,
                         sortType: PermanentGraph.SortType,
                         matchingHeads: Set<Int>?,
                         matchingCommits: Set<Int>?): VisibleGraph<Int> {
    return if (matchingHeads.matchesNothing() || matchingCommits.matchesNothing()) {
      EmptyVisibleGraph.getInstance()
    }
    else {
      dataPack.permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits)
    }
  }

  private fun filterByDetails(dataPack: DataPack,
                              filters: VcsLogFilterCollection,
                              commitCount: CommitCountStage,
                              visibleRoots: Collection<VirtualFile>,
                              matchingHeads: Set<Int>?): FilterByDetailsResult {
    val detailsFilters = filters.detailsFilters
    if (detailsFilters.isEmpty()) return FilterByDetailsResult(null, false, commitCount)

    val dataGetter = index.dataGetter
    val (rootsForIndex, rootsForVcs) = if (dataGetter != null && dataGetter.canFilter(detailsFilters)) {
      visibleRoots.partition { index.isIndexed(it) }
    }
    else {
      kotlin.Pair(emptyList(), visibleRoots.toList())
    }

    val filteredWidthIndex: Set<Int>? = if (rootsForIndex.isNotEmpty()) dataGetter?.filter(detailsFilters) else null

    val headsForVcs = if (rootsForVcs.containsAll(visibleRoots)) matchingHeads
    else getMatchingHeads(dataPack.refsModel, rootsForVcs, filters)
    val filteredWithVcs = filterWithVcs(dataPack.permanentGraph, filters, detailsFilters, headsForVcs, commitCount)

    val filteredCommits: Set<Int>? = union(filteredWidthIndex, filteredWithVcs.matchingCommits)
    return FilterByDetailsResult(filteredCommits, filteredWithVcs.canRequestMore, filteredWithVcs.commitCount)
  }

  private fun filterWithVcs(graph: PermanentGraph<Int>,
                            filters: VcsLogFilterCollection,
                            detailsFilters: List<VcsLogDetailsFilter>,
                            matchingHeads: Set<Int>?,
                            commitCount: CommitCountStage): FilterByDetailsResult {
    var commitCountToTry = commitCount
    if (commitCountToTry == CommitCountStage.INITIAL) {
      val commitsFromMemory = filterInMemory(graph, detailsFilters, matchingHeads).toCommitIndexes()
      if (commitsFromMemory.size >= commitCountToTry.count) {
        return FilterByDetailsResult(commitsFromMemory, true, commitCountToTry)
      }
      commitCountToTry = commitCountToTry.next()
    }

    try {
      val commitsFromVcs = getFilteredDetailsFromTheVcs(logProviders, filters, commitCountToTry.count).toCommitIndexes()
      return FilterByDetailsResult(commitsFromVcs, commitsFromVcs.size >= commitCountToTry.count, commitCountToTry)
    }
    catch (e: VcsException) {
      //TODO show an error balloon or something else for non-ea guys.
      LOG.error(e)
      return FilterByDetailsResult(emptySet(), true, commitCountToTry)
    }
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean {
    return false
  }

  private fun applyHashFilter(dataPack: DataPack,
                              hashes: Collection<String>,
                              sortType: PermanentGraph.SortType,
                              commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage>? {
    val hashFilterResult = ContainerUtil.newHashSet<Int>()
    for (partOfHash in hashes) {
      if (partOfHash.length == VcsLogUtil.FULL_HASH_LENGTH) {
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
    val textFilter = VcsLogFilterObject.fromPatternsList(ContainerUtil.newArrayList(hashes), false)
    val textFilterResult = filterByDetails(dataPack, VcsLogFilterObject.collection(textFilter),
                                           commitCount, dataPack.logProviders.keys, null)
    if (hashFilterResult.isEmpty() && textFilterResult.matchingCommits.matchesNothing()) return null
    val filterResult = if (textFilterResult.matchingCommits == null)
      hashFilterResult
    else
      ContainerUtil.union(hashFilterResult, textFilterResult.matchingCommits)

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, filterResult)
    val visiblePack = VisiblePack(dataPack, visibleGraph, textFilterResult.canRequestMore,
                                  VcsLogFilterObject.collection(fromHashes(hashes), textFilter))
    return Pair.create(visiblePack, textFilterResult.commitCount)
  }

  fun getMatchingHeads(refs: VcsLogRefs,
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

      val filteredByFile = getMatchingHeads(refs, roots)
      val filteredByBranch = getMatchingHeads(refs, branchFilter)
      return HashSet(ContainerUtil.union(ContainerUtil.intersection(filteredByBranch, filteredByFile),
                                         getMatchingHeads(roots, revisionFilter)))
    }

    val filteredByFile = getMatchingHeads(refs, roots)
    if (branchFilter == null) return filteredByFile

    val filteredByBranch = getMatchingHeads(refs, branchFilter)
    return HashSet(ContainerUtil.intersection(filteredByBranch, filteredByFile))
  }

  private fun getMatchingHeads(refs: VcsLogRefs, filter: VcsLogBranchFilter): Set<Int> {
    return refs.branches.filter { filter.matches(it.name) }.toReferencedCommitIndexes()
  }

  private fun getMatchingHeads(roots: Collection<VirtualFile>, filter: VcsLogRevisionFilter): Set<Int> {
    return filter.heads.filter { roots.contains(it.root) }.toCommitIndexes()
  }

  private fun getMatchingHeads(refs: VcsLogRefs, roots: Collection<VirtualFile>): Set<Int> {
    return refs.branches.filter { roots.contains(it.root) }.toReferencedCommitIndexes()
  }

  private fun filterInMemory(permanentGraph: PermanentGraph<Int>,
                             detailsFilters: List<VcsLogDetailsFilter>,
                             matchingHeads: Set<Int>?): Collection<CommitId> {
    val result = ContainerUtil.newArrayList<CommitId>()
    for (commit in permanentGraph.allCommits) {
      val data = getDetailsFromCache(commit.id)
                 ?: // no more continuous details in the cache
                 break
      if (matchesAllFilters(data, permanentGraph, detailsFilters, matchingHeads)) {
        result.add(CommitId(data.id, data.root))
      }
    }
    return result
  }

  private fun matchesAllFilters(commit: VcsCommitMetadata,
                                permanentGraph: PermanentGraph<Int>,
                                detailsFilters: List<VcsLogDetailsFilter>,
                                matchingHeads: Set<Int>?): Boolean {
    val matchesAllDetails = ContainerUtil.and(detailsFilters) { filter -> filter.matches(commit) }
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

internal class FilterByDetailsResult(val matchingCommits: Set<Int>?,
                                     val canRequestMore: Boolean,
                                     val commitCount: CommitCountStage)

@Throws(VcsException::class)
private fun getFilteredDetailsFromTheVcs(providers: Map<VirtualFile, VcsLogProvider>,
                                         filterCollection: VcsLogFilterCollection,
                                         maxCount: Int): Collection<CommitId> {
  val commits = ContainerUtil.newArrayList<CommitId>()

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

    val matchingCommits = providers[root]!!.getCommitsMatchingFilter(root, rootSpecificCollection, maxCount)
    commits.addAll(matchingCommits.map { commit -> CommitId(commit.id, root) })
  }

  return commits
}

fun areFiltersAffectedByIndexing(filters: VcsLogFilterCollection, roots: List<VirtualFile>): Boolean {
  val detailsFilters = filters.detailsFilters
  if (detailsFilters.isEmpty()) return false

  val affectedRoots = VcsLogUtil.getAllVisibleRoots(roots, filters)
  val needsIndex = !affectedRoots.isEmpty()
  if (needsIndex) {
    LOG.debug(filters.toString() + " are affected by indexing of " + affectedRoots)
  }
  return needsIndex
}

internal fun <T> Collection<T>?.matchesNothing(): Boolean {
  return this != null && this.isEmpty()
}

internal fun <T> union(c1: Set<T>?, c2: Set<T>?): Set<T>? {
  if (c1 == null) return c2
  if (c2 == null) return c1
  return ContainerUtil.union(c1, c2)
}
