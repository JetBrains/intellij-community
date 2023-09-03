// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.history.VcsLogFileHistoryFilter
import com.intellij.vcs.log.util.VcsLogUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus

/**
 * Filters the VcsLogData based on the provided filters and commit count.
 *
 * @param filters collection of filters to use
 * @param commitCount the number of commits to filter, when using VCS
 * @return filtered commits, or null if all commits match the filters
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun VcsLogData.filter(filters: VcsLogFilterCollection, commitCount: CommitCountStage = CommitCountStage.ALL): IntSet? {
  return VcsLogFiltererImpl(logProviders, storage, topCommitsCache, commitDetailsGetter, index).filter(dataPack, filters, commitCount)
}

@ApiStatus.Internal
@RequiresBackgroundThread
private fun VcsLogFiltererImpl.filter(dataPack: DataPack,
                                      filters: VcsLogFilterCollection,
                                      commitCount: CommitCountStage = CommitCountStage.ALL): IntSet? {
  val structureFilter = filters.get(STRUCTURE_FILTER)
  require(structureFilter !is VcsLogFileHistoryFilter) {
    "File history filter is not supported"
  }

  val visibleRoots = VcsLogUtil.getAllVisibleRoots(dataPack.logProviders.keys, filters)

  if (!dataPack.isFull || visibleRoots.none { index.isIndexed(it) }) {
    return filterWithVcs(filters, commitCount.count)
  }

  if (filters.filters.all { it is VcsLogDetailsFilter || it is VcsLogRootFilter } && structureFilter == null) {
    val filterByDetailsResult = filterByDetails(dataPack, filters, commitCount, visibleRoots, null, null, false)
    return filterByDetailsResult.matchingCommits
  }

  val (visiblePack, _) = filter(dataPack, VisiblePack.EMPTY, PermanentGraph.SortType.Normal, filters, commitCount)
  if (visiblePack.visibleGraph.visibleCommitCount == dataPack.permanentGraph.allCommits.size) return null

  val result = IntOpenHashSet()
  for (i in 0 until visiblePack.visibleGraph.visibleCommitCount) {
    result.add(visiblePack.visibleGraph.getRowInfo(i).commit)
  }
  return result
}