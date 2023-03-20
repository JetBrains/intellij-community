package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachNodeContainer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogDefaultColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode


internal class ExecutableCell(attachTreeProcessNode: AttachDialogProcessNode,
                              filters: AttachToProcessElementsFilters,
                              columnsLayout: AttachDialogColumnsLayout,
                              private val hasOffset: Boolean = true) : AttachFiltersAwareCell(
  AttachDialogDefaultColumnsLayout.EXECUTABLE_CELL_KEY,
  attachTreeProcessNode,
  filters,
  columnsLayout), AttachNodeContainer<AttachDialogProcessNode> {

  override fun getTextToDisplay(): String = node.item.executableText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.executableTextAttributes ?: super.getTextAttributes()

  override fun getAttachNode(): AttachDialogProcessNode = node

  override fun getTextStartOffset(component: SimpleColoredComponent): Int =
    if (hasOffset)
      (getIcon()?.iconWidth ?: JBUI.scale(16)) + JBUI.scale(8)
    else
      0
}