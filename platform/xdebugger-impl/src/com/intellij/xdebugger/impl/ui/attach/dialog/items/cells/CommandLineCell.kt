package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogDefaultColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class CommandLineCell(attachTreeProcessNode: AttachDialogProcessNode,
                               filters: AttachToProcessElementsFilters,
                               columnsLayout: AttachDialogColumnsLayout)
  : AttachFiltersAwareCell(AttachDialogDefaultColumnsLayout.COMMAND_LINE_CELL_KEY, attachTreeProcessNode,filters, columnsLayout) {

  override fun getTextToDisplay(): String = node.item.commandLineText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.commandLineTextAttributes ?: super.getTextAttributes()
}