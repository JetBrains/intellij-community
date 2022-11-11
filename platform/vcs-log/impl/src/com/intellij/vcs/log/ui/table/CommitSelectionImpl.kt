// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.util.Consumer
import com.intellij.util.EmptyConsumer
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.VisibleGraph

internal class CommitSelectionImpl(private val logData: VcsLogData,
                                   private val visibleGraph: VisibleGraph<Int>,
                                   private val selectedRows: IntArray) : VcsLogCommitSelection {
  override val size get() = selectedRows.size

  override val ids: List<Int> get() = selectedRows.map { getIdAtRow(it) }

  override val commits: List<CommitId>
    get() = getDataForRows(selectedRows) { row -> logData.getCommitId(getIdAtRow(row))!! }
  override val cachedMetadata: List<VcsCommitMetadata>
    get() = getDataForRows(selectedRows) { row -> logData.miniDetailsGetter.getCommitData(getIdAtRow(row)) }
  override val cachedFullDetails: List<VcsFullCommitDetails>
    get() = getDataForRows(selectedRows) { row -> logData.commitDetailsGetter.getCommitData(getIdAtRow(row)) }

  override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) {
    logData.commitDetailsGetter.loadCommitsData(ids, consumer, EmptyConsumer.getInstance(), null)
  }

  private fun getIdAtRow(row: Int) = visibleGraph.getRowInfo(row).commit

  companion object {
    private fun <T> getDataForRows(rows: IntArray, dataGetter: (Int) -> T): List<T> {
      return object : AbstractList<T>() {
        override fun get(index: Int): T = dataGetter(rows[index])
        override val size get() = rows.size
      }
    }
  }
}