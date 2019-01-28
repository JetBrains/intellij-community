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
import com.intellij.openapi.util.Pair
import com.intellij.ui.ScrollingUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.graph.VisibleGraph
import gnu.trove.TIntHashSet

import java.awt.*
import javax.swing.JTable

internal class Selection(private val table: VcsLogGraphTable) {
  private val selectedCommits: TIntHashSet = TIntHashSet()
  private val isOnTop: Boolean
  private val scrollingTarget: ScrollingTarget?

  init {
    val selectedRows = ContainerUtil.sorted(Ints.asList(*table.selectedRows))
    val visibleRows = getVisibleRows(table)
    isOnTop = visibleRows.first == 0

    val graph = table.visibleGraph

    var target: ScrollingTarget? = null
    for (row in selectedRows) {
      if (row < graph.visibleCommitCount) {
        val commit = graph.getRowInfo(row).commit
        selectedCommits.add(commit)
        if (visibleRows.first <= row && row <= visibleRows.second && target == null) {
          target = ScrollingTarget(commit, getTopGap(row))
        }
      }
    }
    if (target == null && visibleRows.first >= 0) {
      target = ScrollingTarget(graph.getRowInfo(visibleRows.first).commit, visibleRows.first)
    }

    scrollingTarget = target
  }

  private fun getTopGap(row: Int) = table.getCellRect(row, 0, false).y - table.visibleRect.y

  private fun getVisibleRows(table: JTable): Pair<Int, Int> {
    val visibleRows = ScrollingUtil.getVisibleRows(table)
    return Pair(visibleRows.first - 1, visibleRows.second)
  }

  fun restore(newVisibleGraph: VisibleGraph<Int>, scrollToSelection: Boolean, permGraphChanged: Boolean) {
    val toSelectAndScroll = findRowsToSelectAndScroll(table.model, newVisibleGraph)
    if (!toSelectAndScroll.first.isEmpty) {
      table.selectionModel.valueIsAdjusting = true
      toSelectAndScroll.first.forEach { row ->
        table.addRowSelectionInterval(row, row)
        true
      }
      table.selectionModel.valueIsAdjusting = false
    }
    if (scrollToSelection) {
      if (isOnTop && permGraphChanged) { // scroll on top when some fresh commits arrive
        scrollToRow(0, 0)
      }
      else if (toSelectAndScroll.second != null) {
        scrollToRow(toSelectAndScroll.second, scrollingTarget!!.topGap)
      }
    }
    // sometimes commits that were selected are now collapsed
    // currently in this case selection disappears
    // in the future we need to create a method in LinearGraphController that allows to calculate visible commit for our commit
    // or answer from collapse action could return a map that gives us some information about what commits were collapsed and where
  }

  private fun scrollToRow(row: Int?, delta: Int?) {
    val startRect = table.getCellRect(row!!, 0, true)
    table.scrollRectToVisible(Rectangle(startRect.x, Math.max(startRect.y - delta!!, 0),
                                        startRect.width, table.visibleRect.height))
  }

  private fun findRowsToSelectAndScroll(model: GraphTableModel,
                                        visibleGraph: VisibleGraph<Int>): Pair<TIntHashSet, Int> {
    val rowsToSelect = TIntHashSet()

    if (model.rowCount == 0) {
      // this should have been covered by facade.getVisibleCommitCount,
      // but if the table is empty (no commits match the filter), the GraphFacade is not updated, because it can't handle it
      // => it has previous values set.
      return Pair.create(rowsToSelect, null)
    }

    var rowToScroll: Int? = null
    var row = 0
    while (row < visibleGraph.visibleCommitCount && (rowsToSelect.size() < selectedCommits.size() || rowToScroll == null)) { //stop iterating if found all hashes
      val commit = visibleGraph.getRowInfo(row).commit
      if (selectedCommits.contains(commit)) {
        rowsToSelect.add(row)
      }
      if (scrollingTarget?.commit == commit) {
        rowToScroll = row
      }
      row++
    }
    return Pair.create(rowsToSelect, rowToScroll)
  }
}

private data class ScrollingTarget(val commit: Int, val topGap: Int)
