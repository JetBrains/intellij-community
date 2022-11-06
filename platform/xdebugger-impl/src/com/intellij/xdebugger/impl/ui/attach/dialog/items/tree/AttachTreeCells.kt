package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogUiInvisiblePresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachTreeDebuggersPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachTableCell
import org.jetbrains.annotations.Nls

internal class CommandLineCell(attachTreeProcessNode: AttachTreeProcessNode,
                               dialogState: AttachDialogState)
  : AttachFiltersAwareCell(attachTreeProcessNode, dialogState.attachTreeColumnSettings, 3,
                           XDebuggerBundle.message("xdebugger.attach.command.line.column.name")) {

  override fun getTextToDisplay(): String = node.item.commandLineText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.commandLineTextAttributes ?: super.getTextAttributes()
}

internal class PidCell(
  private val attachTreeProcessNode: AttachTreeProcessNode,
  dialogState: AttachDialogState) : AttachFiltersAwareCell(attachTreeProcessNode, dialogState.attachTreeColumnSettings, 1,
                                                           XDebuggerBundle.message("xdebugger.attach.pid.column.name")) {
  override fun getTextToDisplay(): String = attachTreeProcessNode.item.processInfo.pid.toString()
}

internal class DebuggersCell(private val attachTreeProcessNode: AttachTreeProcessNode,
                             dialogState: AttachDialogState) : AttachFiltersAwareCell(attachTreeProcessNode,
                                                                                      dialogState.attachTreeColumnSettings, 2,
                                                                                      XDebuggerBundle.message(
                                                                                        "xdebugger.attach.debuggers.column.name")) {
  override fun getTextToDisplay(): String =
    attachTreeProcessNode.item.getGroups().filter { it !is XAttachDialogUiInvisiblePresentationGroup }.sortedBy { it.order }.joinToString(", ") {
      (it as? XAttachTreeDebuggersPresentationProvider)?.getDebuggersShortName() ?: it.groupName
    }
}

internal class ExecutableCell(attachTreeProcessNode: AttachTreeProcessNode,
                              dialogState: AttachDialogState) : AttachFiltersAwareCell(attachTreeProcessNode,
                                                                                       dialogState.attachTreeColumnSettings, 0,
                                                                                       XDebuggerBundle.message(
                                                                                     "xdebugger.attach.executable.column.name")) {

  override fun getTextToDisplay(): String = node.item.executableText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.executableTextAttributes ?: super.getTextAttributes()
}

internal abstract class AttachFiltersAwareCell(
  val node: AttachTreeProcessNode,
  columnsSettingsState: AttachTreeColumnSettingsState,
  index: Int,
  @Nls columnName: String) : AttachTableCell(columnsSettingsState, index, columnName) {
  override fun getTextAttributes(): SimpleTextAttributes =
    if (node.item.getGroups().any() &&
        node.tree.filters.matches(node)) {
      SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES
    }
    else {
      SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
}