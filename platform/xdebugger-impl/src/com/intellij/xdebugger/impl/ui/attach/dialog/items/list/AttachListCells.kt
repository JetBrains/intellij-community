package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachNodeContainer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachTableCell

internal class ExecutableListCell(state: AttachListColumnSettingsState, val node: AttachToProcessListItem) : AttachTableCell(state, 0,
                                                                                                                             XDebuggerBundle.message(
                                                                                                                               "xdebugger.attach.executable.column.name")), AttachNodeContainer<AttachToProcessListItem> {

  override fun getTextToDisplay(): String = node.item.executableText

  override fun getTextStartOffset(component: SimpleColoredComponent): Int =
    (getIcon()?.iconWidth ?: JBUI.scale(16)) + component.iconTextGap

  override fun getAttachNode(): AttachToProcessListItem = node

  override fun getTextAttributes(): SimpleTextAttributes = node.item.executableTextAttributes ?: super.getTextAttributes()
}

class PidListCell(private val pid: Int, state: AttachListColumnSettingsState) : AttachTableCell(state, 1, XDebuggerBundle.message(
  "xdebugger.attach.pid.column.name")) {
  override fun getTextToDisplay(): String {
    return pid.toString()
  }
}

internal class CommandLineListCell(private val node: AttachToProcessListItem, state: AttachListColumnSettingsState) : AttachTableCell(state,
                                                                                                                          2,
                                                                                                                          XDebuggerBundle.message(
                                                                                                                                        "xdebugger.attach.command.line.column.name")) {

  override fun getTextToDisplay(): String = node.item.commandLineText

  override fun getTextAttributes(): SimpleTextAttributes = node.item.commandLineTextAttributes ?: super.getTextAttributes()
}
