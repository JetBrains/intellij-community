package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachTableCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.tree.AttachTreeColumnSettingsState
import org.jetbrains.annotations.Nls

internal abstract class AttachFiltersAwareCell(
  val node: AttachDialogProcessNode,
  val filters: AttachToProcessElementsFilters,
  columnsSettingsState: AttachTreeColumnSettingsState,
  index: Int,
  @Nls columnName: String) : AttachTableCell(columnsSettingsState, index, columnName) {
  override fun getTextAttributes(): SimpleTextAttributes =
    if (node.item.getGroups().any() &&
        filters.matches(node)) {
      SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES
    }
    else {
      SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
}