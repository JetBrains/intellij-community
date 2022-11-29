package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachNodeContainer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode


internal class ExecutableCell(attachTreeProcessNode: AttachDialogProcessNode,
                              filters: AttachToProcessElementsFilters,
                              dialogState: AttachDialogState,
                              private val hasOffset: Boolean = true) : AttachFiltersAwareCell(attachTreeProcessNode,
                                                                                       filters,
                                                                                       dialogState.attachTreeColumnSettings, 0,
                                                                                       XDebuggerBundle.message(
                                                                                         "xdebugger.attach.executable.column.name")), AttachNodeContainer<AttachDialogProcessNode> {

  override fun getTextToDisplay(): String = node.item.executableText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.executableTextAttributes ?: super.getTextAttributes()

  override fun getAttachNode(): AttachDialogProcessNode = node

  override fun getTextStartOffset(component: SimpleColoredComponent): Int = if (hasOffset)
    (getIcon()?.iconWidth ?: JBUI.scale(16)) + JBUI.scale(8)
  else
    0
}