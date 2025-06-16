// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.google.common.primitives.Ints
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.graph.VcsLogVisibleGraphIndex
import com.intellij.vcs.log.graph.VisibleGraph
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.awt.Rectangle
import java.util.function.IntConsumer
import javax.swing.JScrollPane
import javax.swing.JTable
import kotlin.math.max

internal class SelectionSnapshot(private val table: VcsLogGraphTable) {
  private val selectedCommits: IntSet = IntOpenHashSet()
  private val isOnTop: Boolean
  private val scrollingTarget: ScrollingTarget?

  init {
    val selectedRows = ContainerUtil.sorted(Ints.asList(*table.selectedRows))
    val selectedRowsToCommits = mutableMapOf<VcsLogTableIndex, VcsLogCommitStorageIndex>()
    for (row in selectedRows) {
      val commitId = table.model.getId(row) ?: continue
      selectedRowsToCommits[row] = commitId
      selectedCommits.add(commitId)
    }

    val visibleRows = getVisibleRows(table)
    if (visibleRows == null) {
      isOnTop = false
      scrollingTarget = null
    }
    else {
      isOnTop = visibleRows.first == 0

      val visibleRow = selectedRowsToCommits.keys.find { visibleRows.contains(it) } ?: visibleRows.first
      val visibleCommit = selectedRowsToCommits[visibleRow] ?: table.model.getId(visibleRow)
      scrollingTarget = ScrollingTarget(visibleCommit, getTopGap(visibleRow))
    }
  }

  private fun getTopGap(row: VcsLogTableIndex) = table.getCellRect(row, 0, false).y - table.visibleRect.y

  private fun getVisibleRows(table: JTable): IntRange? {
    val visibleRows = ScrollingUtil.getVisibleRows(table)
    val range = IntRange(visibleRows.first, visibleRows.second)
    if (range.isEmpty() || range.first < 0) return null
    return range
  }

  fun restore(graph: VisibleGraph<VcsLogCommitStorageIndex>, scroll: Boolean, permanentGraphChanged: Boolean) {
    val scrollToTop = isOnTop && permanentGraphChanged
    val commitsToRows = mapCommitsToRows(graph, scroll && !scrollToTop)

    table.selectionModel.valueIsAdjusting = true
    selectedCommits.forEach(IntConsumer { commit ->
      val row = commitsToRows[commit]
      if (row != null) {
        table.addRowSelectionInterval(row, row)
      }
    })
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

  private fun mapCommitsToRows(graph: VisibleGraph<VcsLogCommitStorageIndex>, scroll: Boolean): MutableMap<VcsLogCommitStorageIndex, VcsLogVisibleGraphIndex> {
    val commits = mutableSetOf<VcsLogCommitStorageIndex>()
    commits.addAll(selectedCommits)
    if (scroll && scrollingTarget != null && scrollingTarget.commit != null) commits.add(scrollingTarget.commit)
    return mapCommitsToRows(commits, graph)
  }

  private fun mapCommitsToRows(commits: MutableCollection<VcsLogCommitStorageIndex>, graph: VisibleGraph<VcsLogCommitStorageIndex>): MutableMap<VcsLogCommitStorageIndex, VcsLogVisibleGraphIndex> {
    val commitsToRows = mutableMapOf<VcsLogCommitStorageIndex, VcsLogVisibleGraphIndex>()
    for (row in 0 until graph.visibleCommitCount) {
      val commit = graph.getRowInfo(row).commit
      if (commits.remove(commit)) {
        commitsToRows[commit] = row
      }
      if (commits.isEmpty()) break
    }
    return commitsToRows
  }

  private fun scrollToRow(row: VcsLogTableIndex?, delta: Int?) {
    // We're scrolling after changing the table model, and the JTable size must be up to date.
    val scrollPane = ComponentUtil.getParentOfType(JScrollPane::class.java, table)
    scrollPane?.validate()

    val startRect = table.getCellRect(row!!, 0, true)
    table.scrollRectToVisible(Rectangle(startRect.x, max(startRect.y - delta!!, 0),
                                        startRect.width, table.visibleRect.height))
  }
}

private data class ScrollingTarget(val commit: VcsLogCommitStorageIndex?, val topGap: Int)