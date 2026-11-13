// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.util.applyIf
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogGraphData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.recentNodes
import com.intellij.vcs.log.history.VcsLogFileHistoryFilter
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus

/**
 * Filters the VcsLogData based on the provided filters and commit count.
 *
 * @param filters collection of filters to use
 * @param commitCount the number of commits to filter, when using VCS
 * @return filtered commits
 */
@ApiStatus.Experimental
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun VcsLogData.filter(filters: VcsLogFilterCollection, commitCount: CommitCountStage = CommitCountStage.ALL): IntSet {
  return VcsLogFiltererImpl(this).filter(graphData, filters, commitCount)
}

@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
private fun VcsLogFiltererImpl.filter(
  dataPack: VcsLogGraphData,
  filters: VcsLogFilterCollection,
  commitCount: CommitCountStage = CommitCountStage.ALL,
): IntSet {
  val structureFilter = filters.get(STRUCTURE_FILTER)
  require(structureFilter !is VcsLogFileHistoryFilter) {
    "File history filter is not supported"
  }

  val visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.logProviders.keys, filters)

  if (!dataPack.isFull || visibleRoots.none { index.isIndexed(it) }) {
    return filterWithVcs(filters, PermanentGraph.Options.Default, commitCount.count)
  }

  if (filters.filters.all { it is VcsLogDetailsFilter || it is VcsLogRootFilter } && structureFilter == null) {
    val filterByDetailsResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, null, null,
                                                PermanentGraph.Options.Default, false)
    val matchingCommits = filterByDetailsResult.matchingCommits
    if (matchingCommits != null) return matchingCommits

    val commits = dataPack.permanentGraph.allCommits.applyIf(!commitCount.isAll()) { take(commitCount.count) }
    return commits.mapTo(IntOpenHashSet()) { it.id }
  }

  val (visiblePack, _) = filter(dataPack, VisiblePack.EMPTY, PermanentGraph.Options.Default, filters, commitCount)

  val maxCommits = visiblePack.visibleGraph.visibleCommitCount.applyIf(!commitCount.isAll()) { coerceAtMost(commitCount.count) }
  val result = IntOpenHashSet()
  for (i in 0 until maxCommits) {
    result.add(visiblePack.visibleGraph.getRowInfo(i).commit)
  }
  return result
}

/**
 * Returns the list of most recent commits from the given branches.
 *
 * @param branchNames the names of the branches to get the recent commits from
 * @param limit the maximum number of commits to return
 */
@ApiStatus.Experimental
@RequiresBackgroundThread(generateAssertion = true)
fun VcsLogData.getRecentCommits(branchNames: List<String>, limit: Int): List<VcsLogCommitStorageIndex> {
  val branchFilters = VcsLogFilterObject.collection(VcsLogFilterObject.fromBranches(branchNames))
  val matchingHeads = VcsLogFiltererImpl(this).getMatchingHeads(graphData.refsModel, graphData.logProviders.keys, branchFilters)
                      ?: return emptyList()
  if (matchingHeads.isEmpty()) return emptyList()

  @Suppress("UNCHECKED_CAST") val permanentGraph = graphData.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex>
                                                   ?: return emptyList()
  val startIds = matchingHeads.map { permanentGraph.permanentCommitsInfo.getNodeId(it) }
  val recentNodes = permanentGraph.linearGraph.recentNodes(startIds, limit)
  return recentNodes.map { permanentGraph.permanentCommitsInfo.getCommitId(it) }
}