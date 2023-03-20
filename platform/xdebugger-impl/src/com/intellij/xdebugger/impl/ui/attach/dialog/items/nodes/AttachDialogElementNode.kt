package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import javax.swing.table.TableCellRenderer

interface AttachDialogElementNode {
  fun visit(filters: AttachToProcessElementsFilters): Boolean

  fun getProcessItem(): AttachDialogProcessItem?

  fun getValueAtColumn(column: Int): Any

  fun getRenderer(column: Int): TableCellRenderer?
}