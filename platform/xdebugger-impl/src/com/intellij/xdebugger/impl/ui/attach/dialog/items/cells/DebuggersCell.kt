package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogUiInvisiblePresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachTreeDebuggersPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogDefaultColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class DebuggersCell(private val attachTreeProcessNode: AttachDialogProcessNode,
                             filters: AttachToProcessElementsFilters,
                             columnsLayout: AttachDialogColumnsLayout) : AttachFiltersAwareCell(
                                                                                      AttachDialogDefaultColumnsLayout.DEBUGGERS_CELL_KEY,
                                                                                      attachTreeProcessNode,
                                                                                      filters,
                                                                                      columnsLayout) {
  override fun getTextToDisplay(): String =
    attachTreeProcessNode.item.getGroups().filter { it !is XAttachDialogUiInvisiblePresentationGroup }.sortedBy { it.order }.joinToString(
      ", ") {
      (it as? XAttachTreeDebuggersPresentationProvider)?.getDebuggersShortName() ?: it.groupName
    }
}
