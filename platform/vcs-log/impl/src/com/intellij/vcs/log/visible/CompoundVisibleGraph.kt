// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.vcs.log.graph.RowInfo
import com.intellij.vcs.log.graph.VcsLogVisibleGraphIndex
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.actions.ActionController
import kotlin.math.max

internal class CompoundVisibleGraph<CommitId : Any>(
  private val firstGraph: VisibleGraph<CommitId>,
  private val secondGraph: VisibleGraph<CommitId>,
) : VisibleGraph<CommitId> {
  override val visibleCommitCount: Int
    get() = max(firstGraph.visibleCommitCount, secondGraph.visibleCommitCount)

  override fun getRowInfo(visibleRow: VcsLogVisibleGraphIndex): RowInfo<CommitId> =
    when {
      firstGraph.containsRow(visibleRow) -> firstGraph.getRowInfo(visibleRow)
      secondGraph.containsRow(visibleRow) -> secondGraph.getRowInfo(visibleRow)
      else -> error("""Cannot get visibleRow=$visibleRow from firstGraph=${firstGraph.visibleCommitCount} 
                 and secondGraph=${secondGraph.visibleCommitCount}""".trimMargin())
    }

  override fun getVisibleRowIndex(id: CommitId): VcsLogVisibleGraphIndex? {
    return firstGraph.getVisibleRowIndex(id) ?: secondGraph.getVisibleRowIndex(id)
  }

  override val actionController: ActionController<CommitId>
    get() = secondGraph.actionController

  override val recommendedWidth: Int
    get() = secondGraph.recommendedWidth

  private fun VisibleGraph<CommitId>.containsRow(visibleRow: VcsLogVisibleGraphIndex): Boolean {
    return visibleRow in 0 until visibleCommitCount
  }
}
