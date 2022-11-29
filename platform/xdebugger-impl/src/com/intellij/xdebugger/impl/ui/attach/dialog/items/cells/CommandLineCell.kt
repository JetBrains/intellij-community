package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class CommandLineCell(attachTreeProcessNode: AttachDialogProcessNode,
                               filters: AttachToProcessElementsFilters,
                               dialogState: AttachDialogState)
  : AttachFiltersAwareCell(attachTreeProcessNode,filters, dialogState.attachTreeColumnSettings, 3,
                           XDebuggerBundle.message("xdebugger.attach.command.line.column.name")) {

  override fun getTextToDisplay(): String = node.item.commandLineText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.commandLineTextAttributes ?: super.getTextAttributes()
}