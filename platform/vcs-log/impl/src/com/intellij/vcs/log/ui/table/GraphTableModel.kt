// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataProvider
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.RowInfo
import com.intellij.vcs.log.graph.RowType
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import org.jetbrains.annotations.ApiStatus
import javax.swing.table.AbstractTableModel
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class GraphTableModel @ApiStatus.Internal constructor(
  val logData: VcsLogData,
  private val requestMore: Runnable,
  internal val properties: VcsLogUiProperties
) : AbstractTableModel(), VcsLogCommitListModel {
  var visiblePack: VisiblePack by Delegates.observable(VisiblePack.EMPTY) { _, _, _ -> fireTableDataChanged() }

  override val dataProvider: VcsLogDataProvider = logData

  override fun getRowCount(): Int {
    return visiblePack.visibleGraph.visibleCommitCount
  }

  override fun getColumnCount(): Int {
    return VcsLogColumnManager.getInstance().getModelColumnsCount()
  }

  override fun getColumnName(column: Int): String {
    return getColumn(column).localizedName
  }

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    return getValueAt(rowIndex, getColumn(columnIndex)) as Any
  }

  fun <T> getValueAt(rowIndex: Int, column: VcsLogColumn<T>): T {
    if (rowIndex >= rowCount - 1 && VcsLogUtil.canRequestMore(visiblePack)) {
      requestMore.run()
    }

    return try {
      column.getValue(this, rowIndex) ?: column.getStubValue(this)
    }
    catch (ignore: ProcessCanceledException) {
      column.getStubValue(this)
    }
    catch (t: Throwable) {
      LOG.error("Failed to get information for the log table", t)
      column.getStubValue(this)
    }
  }

  override fun getId(row: Int): Int? {
    return getGraphRowInfo(row)?.commit
  }

  private fun getGraphRowInfo(row: Int): RowInfo<Int>? {
    return visiblePack.visibleGraph.getRowInfo(row);
  }

  fun getRowType(row: Int): RowType? {
    return getGraphRowInfo(row)?.rowType
  }

  fun getRootAtRow(row: Int): VirtualFile? {
    val head = getGraphRowInfo(row)?.getOneOfHeads() ?: return null
    return visiblePack.getRootAtHead(head)
  }

  fun getPrintElements(row: Int): Collection<PrintElement> {
    return if (VisiblePack.NO_GRAPH_INFORMATION.get(visiblePack, false)) emptyList()
    else getGraphRowInfo(row)?.printElements.orEmpty()
  }

  fun getRefsAtRow(row: Int): List<VcsRef> {
    val root = getRootAtRow(row)
    val id = getId(row) ?: return emptyList()
    val refsModel = visiblePack.dataPack.refsModel
    return if (root != null) refsModel.refsToCommit(root, id) else refsModel.refsToCommit(id)
  }

  @JvmOverloads
  fun getCommitMetadata(row: Int, load: Boolean = false): VcsCommitMetadata? {
    val commit = getId(row) ?: return null
    val commitsToLoad = if (load) {
      val startRowIndex = max(0, (row - UP_PRELOAD_COUNT))
      val endRowIndex = min(row + DOWN_PRELOAD_COUNT, rowCount)
      (startRowIndex until endRowIndex).asSequence().mapNotNull { getGraphRowInfo(it)?.commit }.asIterable()
    }
    else ContainerUtil.emptyList<Int>()
    return logData.miniDetailsGetter.getCommitData(commit, commitsToLoad)
  }

  fun createSelection(rows: IntArray): VcsLogCommitSelection {
    return CommitSelectionImpl(logData, visiblePack.visibleGraph, rows)
  }

  fun fromGraphToTableRow(graphRow: Int): Int = graphRow

  private companion object {
    private const val UP_PRELOAD_COUNT = 20
    private const val DOWN_PRELOAD_COUNT = 40

    private val LOG = Logger.getInstance(GraphTableModel::class.java)

    private fun getColumn(modelIndex: Int): VcsLogColumn<*> {
      return VcsLogColumnManager.getInstance().getColumn(modelIndex)
    }
  }
}
