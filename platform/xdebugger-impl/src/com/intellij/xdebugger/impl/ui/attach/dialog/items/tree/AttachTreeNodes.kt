package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachSelectionIgnoredNode
import javax.swing.table.TableCellRenderer


internal class AttachTreeRootNode : AttachDialogElementNode, AttachSelectionIgnoredNode {

  override fun getValueAtColumn(column: Int): Any {
    return this
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return true
  }

  override fun getProcessItem(): AttachDialogProcessItem? = null
}