package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.CommandLineCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.DebuggersCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.ExecutableCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.PidCell
import javax.swing.table.TableCellRenderer

internal class AttachDialogProcessNode(
  val item: AttachDialogProcessItem,
  private val filters: AttachToProcessElementsFilters,
  private val state: AttachDialogState): AttachDialogElementNode {

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(item)
  }

  override fun getProcessItem(): AttachDialogProcessItem = item

  override fun getValueAtColumn(column: Int): Any {
    if (column == 0) return ExecutableCell(this, filters, state)
    if (column == 1) return PidCell(this, filters, state)
    if (column == 2) return DebuggersCell(this, filters, state)
    if (column == 3) return CommandLineCell(this, filters, state)
    throw IllegalStateException("Unexpected column number: $column")
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null
}