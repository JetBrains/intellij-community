package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogUiInvisiblePresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachTreeDebuggersPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.isListMerged
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachColumnSettingsState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachNodeContainer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachTableCell

internal class ExecutableListCell(state: AttachColumnSettingsState, val node: AttachToProcessListItem) : AttachTableCell(state, 0,
                                                                                                                             XDebuggerBundle.message(
                                                                                                                               "xdebugger.attach.executable.column.name")), AttachNodeContainer<AttachToProcessListItem> {

  override fun getTextToDisplay(): String = node.item.executableText

  override fun getTextStartOffset(component: SimpleColoredComponent): Int =
    (getIcon()?.iconWidth ?: JBUI.scale(16)) + component.iconTextGap

  override fun getAttachNode(): AttachToProcessListItem = node

  override fun getTextAttributes(): SimpleTextAttributes = node.item.executableTextAttributes ?: super.getTextAttributes()
}

class PidListCell(private val pid: Int, state: AttachColumnSettingsState) : AttachTableCell(state, 1, XDebuggerBundle.message(
  "xdebugger.attach.pid.column.name")) {
  override fun getTextToDisplay(): String {
    return pid.toString()
  }
}

internal class DebuggersListCell(private val node: AttachToProcessListItem, state: AttachColumnSettingsState) : AttachTableCell(state,
                                                                                                                                      2,
                                                                                                                                      XDebuggerBundle.message(
                                                                                                                                        "xdebugger.attach.debuggers.column.name")) {

  override fun getTextToDisplay(): String = node.item.getGroups().filter { it !is XAttachDialogUiInvisiblePresentationGroup }.sortedBy { it.order }.joinToString(", ") {
    (it as? XAttachTreeDebuggersPresentationProvider)?.getDebuggersShortName() ?: it.groupName
  }
}


internal class CommandLineListCell(private val node: AttachToProcessListItem, state: AttachColumnSettingsState) : AttachTableCell(state,
                                                                                                                          if (isListMerged()) 3 else 2,
                                                                                                                          XDebuggerBundle.message(
                                                                                                                                        "xdebugger.attach.command.line.column.name")) {

  override fun getTextToDisplay(): String = node.item.commandLineText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.commandLineTextAttributes ?: super.getTextAttributes()
}
