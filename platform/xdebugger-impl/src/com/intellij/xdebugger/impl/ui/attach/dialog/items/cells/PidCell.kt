package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class PidCell(
  private val attachTreeProcessNode: AttachDialogProcessNode,
  filters: AttachToProcessElementsFilters,
  dialogState: AttachDialogState) : AttachFiltersAwareCell(attachTreeProcessNode, filters, dialogState.attachTreeColumnSettings, 1,
                                                           XDebuggerBundle.message("xdebugger.attach.pid.column.name")) {
  override fun getTextToDisplay(): String = attachTreeProcessNode.item.processInfo.pid.toString()
}
