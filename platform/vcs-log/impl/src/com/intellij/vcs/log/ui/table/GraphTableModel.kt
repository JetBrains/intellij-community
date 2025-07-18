// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.*
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

/**
 * Row index used in [VcsLogGraphTable].
 */
typealias VcsLogTableIndex = Int

@ApiStatus.NonExtendable
open class GraphTableModel @ApiStatus.Internal constructor(
  val logData: VcsLogData,
  private val requestMore: Runnable,
  internal val properties: VcsLogUiProperties,
) : AbstractTableModel(), VcsLogCommitListModel {
  var visiblePack: VisiblePack by Delegates.observable(VisiblePack.EMPTY) { _, _, _ -> fireTableDataChanged() }

  final override val dataProvider: VcsLogDataProvider = logData

  override fun getRowCount(): Int {
    return visiblePack.visibleGraph.visibleCommitCount
  }

  final override fun getColumnCount(): Int {
    return VcsLogColumnManager.getInstance().getModelColumnsCount()
  }

  final override fun getColumnName(column: Int): String {
    return getColumn(column).localizedName
  }

  final override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    return getValueAt(rowIndex, getColumn(columnIndex)) as Any
  }

  open fun <T> getValueAt(rowIndex: VcsLogTableIndex, column: VcsLogColumn<T>): T {
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

  final override fun getId(row: VcsLogTableIndex): VcsLogCommitStorageIndex? {
    return getGraphRowInfo(row)?.commit
  }

  private fun getGraphRowInfo(row: VcsLogTableIndex): RowInfo<VcsLogCommitStorageIndex>? {
    val graphRow = fromTableToGraphRow(row) ?: return null
    return visiblePack.visibleGraph.getRowInfo(graphRow)
  }

  fun getRowType(row: VcsLogTableIndex): RowType? {
    return getGraphRowInfo(row)?.rowType
  }

  fun getRootAtRow(row: VcsLogTableIndex): VirtualFile? {
    val head = getGraphRowInfo(row)?.getOneOfHeads() ?: return null
    return visiblePack.getRootAtHead(head)
  }

  open fun getPrintElements(row: VcsLogTableIndex): Collection<PrintElement> {
    return if (VisiblePack.NO_GRAPH_INFORMATION.get(visiblePack, false)) emptyList()
    else getGraphRowInfo(row)?.printElements?.withPossibleHeadNode(row).orEmpty()
  }

  private fun Collection<PrintElement>.withPossibleHeadNode(row: VcsLogTableIndex): Collection<PrintElement> {
    if (this.isEmpty()) return emptyList()

    val haveHead = getRefsAtRow(row).find { it.name == VcsLogUtil.HEAD } != null
    if (!haveHead) return this

    val headNodeElement = filterIsInstance<NodePrintElement>().singleOrNull()?.let(::HeadNodePrintElement)
    if (headNodeElement == null) return this

    return map { element ->
      when (element) {
        is NodePrintElement -> headNodeElement
        else -> element
      }
    }
  }

  fun getRefsAtRow(row: VcsLogTableIndex): List<VcsRef> {
    val root = getRootAtRow(row)
    val id = getId(row) ?: return emptyList()
    val refsModel = visiblePack.dataPack.refsModel
    return if (root != null) refsModel.refsToCommit(root, id) else refsModel.refsToCommit(id)
  }

  @JvmOverloads
  fun getCommitMetadata(row: VcsLogTableIndex, load: Boolean = false): VcsCommitMetadata? {
    val graphRow = fromTableToGraphRow(row) ?: return null
    val commit = getId(row) ?: return null
    val commitsToLoad = if (load) {
      val startRowIndex = max(0, (graphRow - UP_PRELOAD_COUNT))
      val endRowIndex = min(graphRow + DOWN_PRELOAD_COUNT, rowCount)
      (startRowIndex until endRowIndex).asSequence().mapNotNull { getGraphRowInfo(it)?.commit }.asIterable()
    }
    else ContainerUtil.emptyList()
    return logData.miniDetailsGetter.getCommitData(commit, commitsToLoad)
  }

  fun createSelection(rows: IntArray): VcsLogCommitSelection {
    return CommitSelectionImpl(logData, visiblePack.visibleGraph, fromTableToGraphRows(rows))
  }

  open fun fromGraphToTableRow(graphRow: VcsLogVisibleGraphIndex): VcsLogTableIndex = graphRow
  protected open fun fromTableToGraphRow(tableRow: VcsLogTableIndex): VcsLogVisibleGraphIndex? = tableRow
  protected open fun fromTableToGraphRows(rows: IntArray): IntArray = rows

  @ApiStatus.Internal
  companion object {
    private const val UP_PRELOAD_COUNT = 20
    private const val DOWN_PRELOAD_COUNT = 40

    private val LOG = Logger.getInstance(GraphTableModel::class.java)

    @JvmStatic
    protected fun getColumn(modelIndex: Int): VcsLogColumn<*> {
      return VcsLogColumnManager.getInstance().getColumn(modelIndex)
    }
  }
}
