package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogUiInvisiblePresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachTreeDebuggersPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class DebuggersCell(private val attachTreeProcessNode: AttachDialogProcessNode,
                             filters: AttachToProcessElementsFilters,
                             dialogState: AttachDialogState) : AttachFiltersAwareCell(attachTreeProcessNode,
                                                                                      filters,
                                                                                      dialogState.attachTreeColumnSettings, 2,
                                                                                      XDebuggerBundle.message(
                                                                                        "xdebugger.attach.debuggers.column.name")) {
  override fun getTextToDisplay(): String =
    attachTreeProcessNode.item.getGroups().filter { it !is XAttachDialogUiInvisiblePresentationGroup }.sortedBy { it.order }.joinToString(
      ", ") {
      (it as? XAttachTreeDebuggersPresentationProvider)?.getDebuggersShortName() ?: it.groupName
    }
}
