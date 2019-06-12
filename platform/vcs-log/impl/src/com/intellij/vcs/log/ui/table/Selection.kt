/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.table

import com.google.common.primitives.Ints
import com.intellij.ui.ScrollingUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.util.TroveUtil
import gnu.trove.TIntHashSet
import java.awt.Rectangle
import javax.swing.JTable

internal class Selection(private val table: VcsLogGraphTable) {
  private val selectedCommits: TIntHashSet = TIntHashSet()
  private val isOnTop: Boolean
  private val scrollingTarget: ScrollingTarget?

  init {
    val selectedRows = ContainerUtil.sorted(Ints.asList(*table.selectedRows))
    val selectedRowsToCommits = selectedRows.associateWith { table.visibleGraph.getRowInfo(it).commit }
    TroveUtil.addAll(selectedCommits, selectedRowsToCommits.values)

    val visibleRows = getVisibleRows(table)
    if (visibleRows == null) {
      isOnTop = false
      scrollingTarget = null
    }
    else {
      isOnTop = visibleRows.first == 0

      val visibleRow = selectedRowsToCommits.keys.find { visibleRows.contains(it) } ?: visibleRows.first
      val visibleCommit = selectedRowsToCommits[visibleRow] ?: table.visibleGraph.getRowInfo(visibleRow).commit
      scrollingTarget = ScrollingTarget(visibleCommit, getTopGap(visibleRow))
    }
  }

  private fun getTopGap(row: Int) = table.getCellRect(row, 0, false).y - table.visibleRect.y

  private fun getVisibleRows(table: JTable): IntRange? {
    val visibleRows = ScrollingUtil.getVisibleRows(table)
    val range = IntRange(visibleRows.first, visibleRows.second)
    if (range.isEmpty() || range.first < 0) return null
    return range
  }

  fun restore(graph: VisibleGraph<Int>, scroll: Boolean, permanentGraphChanged: Boolean) {
    val scrollToTop = isOnTop && permanentGraphChanged
    val commitsToRows = mapCommitsToRows(graph, scroll && !scrollToTop)

    table.selectionModel.valueIsAdjusting = true
    for (commit in selectedCommits) {
      val row = commitsToRows[commit]
      if (row != null) {
        table.addRowSelectionInterval(row, row)
      }
    }
    table.selectionModel.valueIsAdjusting = false

    if (scroll) {
      if (scrollToTop) {
        scrollToRow(0, 0)
      }
      else if (scrollingTarget != null) {
        val scrollingTargetRow = commitsToRows[scrollingTarget.commit]
        if (scrollingTargetRow != null) {
          scrollToRow(scrollingTargetRow, scrollingTarget.topGap)
        }
      }
    }
  }

  private fun mapCommitsToRows(graph: VisibleGraph<Int>, scroll: Boolean): MutableMap<Int, Int> {
    val commits = mutableSetOf<Int>()
    TroveUtil.addAll(commits, selectedCommits)
    if (scroll && scrollingTarget != null) commits.add(scrollingTarget.commit)
    return mapCommitsToRows(commits, graph)
  }

  private fun mapCommitsToRows(commits: MutableCollection<Int>, graph: VisibleGraph<Int>): MutableMap<Int, Int> {
    val commitsToRows = mutableMapOf<Int, Int>()
    for (row in 0 until graph.visibleCommitCount) {
      val commit = graph.getRowInfo(row).commit
      if (commits.remove(commit)) {
        commitsToRows[commit] = row
      }
      if (commits.isEmpty()) break
    }
    return commitsToRows
  }

  private fun scrollToRow(row: Int?, delta: Int?) {
    val startRect = table.getCellRect(row!!, 0, true)
    table.scrollRectToVisible(Rectangle(startRect.x, Math.max(startRect.y - delta!!, 0),
                                        startRect.width, table.visibleRect.height))
  }
}

private data class ScrollingTarget(val commit: Int, val topGap: Int)