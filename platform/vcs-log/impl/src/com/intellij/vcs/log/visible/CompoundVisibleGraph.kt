// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.vcs.log.graph.RowInfo
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.actions.ActionController
import kotlin.math.max

internal class CompoundVisibleGraph<CommitId : Any>(private val firstGraph: VisibleGraph<CommitId>,
                                                    private val secondGraph: VisibleGraph<CommitId>) : VisibleGraph<CommitId> {
  override fun getVisibleCommitCount(): Int {
    return max(firstGraph.visibleCommitCount, secondGraph.visibleCommitCount)
  }

  override fun getRowInfo(visibleRow: Int): RowInfo<CommitId> =
    when {
      firstGraph.containsRow(visibleRow) -> firstGraph.getRowInfo(visibleRow)
      secondGraph.containsRow(visibleRow) -> secondGraph.getRowInfo(visibleRow)
      else -> error("""Cannot get visibleRow=$visibleRow from firstGraph=${firstGraph.visibleCommitCount} 
                 and secondGraph=${secondGraph.visibleCommitCount}""".trimMargin())
    }

  override fun getVisibleRowIndex(id: CommitId): Int? {
    return firstGraph.getVisibleRowIndex(id) ?: secondGraph.getVisibleRowIndex(id)
  }

  override fun getActionController(): ActionController<CommitId> {
    return secondGraph.actionController
  }

  override fun getRecommendedWidth(): Int {
    return secondGraph.recommendedWidth
  }

  private fun VisibleGraph<CommitId>.containsRow(visibleRow: Int): Boolean {
    return visibleRow in 0 until visibleCommitCount
  }
}
