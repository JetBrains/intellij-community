package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import javax.swing.table.TableCellRenderer

class AttachDialogProcessNode(
  val item: AttachDialogProcessItem,
  private val filters: AttachToProcessElementsFilters,
  private val columnsLayout: AttachDialogColumnsLayout): AttachDialogElementNode {

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(item)
  }

  override fun getProcessItem(): AttachDialogProcessItem = item

  override fun getValueAtColumn(column: Int): Any {
    return columnsLayout.createCell(column, this, filters, false)
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null
}