package com.intellij.xdebugger.impl.ui.attach.dialog.items.columns

import com.intellij.ui.table.JBTable
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.AttachTableCellRenderer
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

internal fun TableColumnModel.applyColumnsLayout(layout: AttachDialogColumnsLayout) {
  for (index in 0 until layout.getColumnsCount()) {
    val columnKey = layout.getColumnKey(index)
    val column = TableColumn(index).apply { identifier = index; headerValue = layout.getColumnName(columnKey) }
    addColumn(column)
    column.minWidth = AttachDialogState.COLUMN_MINIMUM_WIDTH
    column.cellRenderer = AttachTableCellRenderer()
    column.preferredWidth = layout.getColumnWidth(columnKey)
    column.addPropertyChangeListener { if (it.propertyName == "width") layout.setColumnWidth(columnKey, it.newValue as Int) }
  }
}

internal fun JBTable.applyColumnsLayout(layout: AttachDialogColumnsLayout) {
  for (index in 0 until layout.getColumnsCount()) {
    val columnKey = layout.getColumnKey(index)
    val column = columnModel.getColumn(index)
    column.minWidth = AttachDialogState.COLUMN_MINIMUM_WIDTH
    column.preferredWidth = layout.getColumnWidth(columnKey)
    column.addPropertyChangeListener { if (it.propertyName == "width") layout.setColumnWidth(columnKey, it.newValue as Int) }
  }
}