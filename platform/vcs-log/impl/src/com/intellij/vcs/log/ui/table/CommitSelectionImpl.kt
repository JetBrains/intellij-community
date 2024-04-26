// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.VisibleGraph
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class CommitSelectionImpl(private val logData: VcsLogData,
                          private val visibleGraph: VisibleGraph<Int>,
                          override val rows: IntArray) : VcsLogCommitSelection {
  override val ids: List<Int> get() = rows.lazyMap(::getIdAtRow)

  override val commits: List<CommitId>
    get() = lazyMap { id -> logData.getCommitId(id)!! }
  override val cachedMetadata: List<VcsCommitMetadata>
    get() = lazyMap(logData.miniDetailsGetter::getCachedDataOrPlaceholder)
  override val cachedFullDetails: List<VcsFullCommitDetails>
    get() = lazyMap(logData.commitDetailsGetter::getCachedDataOrPlaceholder)

  override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) {
    logData.commitDetailsGetter.loadCommitsData(ids, consumer::accept, { }, null)
  }

  private fun getIdAtRow(row: Int) = visibleGraph.getRowInfo(row).commit

  companion object {
    private fun <T> IntArray.lazyMap(transform: (Int) -> T): List<T> {
      return object : AbstractList<T>() {
        override fun get(index: Int): T = transform(this@lazyMap[index])
        override val size get() = this@lazyMap.size
      }
    }
  }
}