// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI

class MergeConflictsTreeTable(private val tableModel: ListTreeTableModelOnColumns) : TreeTable(tableModel) {
  init {
    getTableHeader().reorderingAllowed = false
    tree.isRootVisible = false
    if (tableModel.columnCount > 1) setShowColumns(true)
  }

  override fun doLayout() {
    if (getTableHeader().resizingColumn == null) {
      updateColumnSizes()
    }
    super.doLayout()
  }

  private fun updateColumnSizes() {
    for ((index, columnInfo) in tableModel.columns.withIndex()) {
      val column = columnModel.getColumn(index)
      columnInfo.maxStringValue?.let {
        val width = calcColumnWidth(it, columnInfo)
        column.preferredWidth = width
      }
    }

    var size = width
    val fileColumn = 0
    for (i in 0 until tableModel.columns.size) {
      if (i == fileColumn) continue
      size -= columnModel.getColumn(i).preferredWidth
    }

    columnModel.getColumn(fileColumn).preferredWidth = Math.max(size, JBUI.scale(200))
  }

  private fun calcColumnWidth(maxStringValue: String, columnInfo: ColumnInfo<Any, Any>): Int {
    val columnName = StringUtil.shortenTextWithEllipsis(columnInfo.name, 15, 7, true)
    return Math.max(getFontMetrics(font).stringWidth(maxStringValue),
                    getFontMetrics(tableHeader.font).stringWidth(columnName)) + columnInfo.additionalWidth
  }
}