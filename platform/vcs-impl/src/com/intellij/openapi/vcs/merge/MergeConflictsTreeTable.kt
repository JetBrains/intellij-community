// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.treeStructure.treetable.DefaultTreeTableExpander
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.MouseEvent
import kotlin.math.max

@ApiStatus.Internal
class MergeConflictsTreeTable(private val tableModel: ListTreeTableModelOnColumns) : TreeTable(tableModel), UiDataProvider {
  init {
    getTableHeader().reorderingAllowed = false
    tree.isRootVisible = false
    if (tableModel.columnCount > 1) setShowColumns(true)
  }

  override fun doLayout() {
    setInitialColumnPreferredWidthIfNeeded()
    var remainingSize = width
    for (i in tableModel.columns.indices) {
      if (i == FILE_COLUMN_INDEX) continue
      remainingSize -= columnModel.getColumn(i).width
    }
    columnModel.getColumn(FILE_COLUMN_INDEX).preferredWidth = remainingSize
    super.doLayout()
  }

  private var initialWidthSet = false
  private fun setInitialColumnPreferredWidthIfNeeded() {
    if (initialWidthSet) return
    for ((index, columnInfo) in tableModel.columns.withIndex()) {
      columnModel.getColumn(index).preferredWidth = columnInfo.calcColumnWidth()
    }

    initialWidthSet = true
  }

  private fun ColumnInfo<Any, Any>.calcColumnWidth(): Int {
    val maxCellWidth = maxStringValue?.let { getFontMetrics(font).stringWidth(it) } ?: 0
    val columnName = StringUtil.shortenTextWithEllipsis(name, 15, 7, true)
    val columnWidth = getFontMetrics(tableHeader.font).stringWidth(columnName) + additionalWidth
    return max(maxCellWidth, columnWidth)
  }

  var toolTipTextProvider: ((e: MouseEvent) -> String?)? = null

  override fun getToolTipText(e: MouseEvent): String? = toolTipTextProvider?.invoke(e) ?: super.getToolTipText(e)

  /**
   * Returns the tag of the tree renderer fragment at [point], or `null` if no tagged fragment is there.
   * [point] is in the table coordinates.
   */
  fun getTreeFragmentTagAt(point: Point): Any? {
    val row = rowAtPoint(point)
    val column = columnAtPoint(point)
    if (row == -1 || column == -1 || !isTreeColumn(column)) return null
    val path = tree.getPathForRow(row) ?: return null
    val pathBounds = tree.getPathBounds(path) ?: return null
    val node = path.lastPathComponent
    val renderer = tree.cellRenderer.getTreeCellRendererComponent(tree, node, isRowSelected(row), tree.isExpanded(row),
                                                                  tree.model.isLeaf(node), row, false)
    return (renderer as? SimpleColoredComponent)?.getFragmentTagAt(point.x - pathBounds.x)
  }

  private val treeExpander = DefaultTreeTableExpander(this)

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
  }

  companion object {
    private const val FILE_COLUMN_INDEX = 0
  }
}