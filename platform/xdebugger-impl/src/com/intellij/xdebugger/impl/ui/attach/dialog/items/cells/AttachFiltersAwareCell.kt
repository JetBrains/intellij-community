package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal abstract class AttachFiltersAwareCell(
  columnKey: String,
  val node: AttachDialogProcessNode,
  val filters: AttachToProcessElementsFilters,
  columnsLayout: AttachDialogColumnsLayout) : AttachTableCell(columnKey, columnsLayout) {
  override fun getTextAttributes(): SimpleTextAttributes =
    if (node.item.getGroups().any() &&
        filters.matches(node)) {
      SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES
    }
    else {
      SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
}