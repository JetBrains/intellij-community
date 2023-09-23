// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.DataGetter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.VisibleGraph
import java.util.function.Consumer

internal class CommitSelectionImpl(private val logData: VcsLogData,
                                   private val visibleGraph: VisibleGraph<Int>,
                                   private val selectedRows: IntArray) : VcsLogCommitSelection {
  override val size get() = selectedRows.size

  override val ids: List<Int> get() = selectedRows.map { getIdAtRow(it) }

  override val commits: List<CommitId>
    get() = getDetails { id -> logData.getCommitId(id)!! }
  override val cachedMetadata: List<VcsCommitMetadata>
    get() = getCachedDetails(logData.miniDetailsGetter)
  override val cachedFullDetails: List<VcsFullCommitDetails>
    get() = getCachedDetails(logData.commitDetailsGetter)

  override fun <T> getDetails(detailsGetter: (Int) -> T): List<T> {
    return getDataForRows(selectedRows) { row -> detailsGetter(getIdAtRow(row)) }
  }

  override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) {
    logData.commitDetailsGetter.loadCommitsData(ids, consumer::accept, { }, null)
  }

  private fun getIdAtRow(row: Int) = visibleGraph.getRowInfo(row).commit

  companion object {
    private fun <T> getDataForRows(rows: IntArray, dataGetter: (Int) -> T): List<T> {
      return object : AbstractList<T>() {
        override fun get(index: Int): T = dataGetter(rows[index])
        override val size get() = rows.size
      }
    }

    internal fun <T : VcsShortCommitDetails> VcsLogCommitSelection.getCachedDetails(dataGetter: DataGetter<T>): List<T> {
      return getDetails(dataGetter::getCachedDataOrPlaceholder)
    }
  }
}