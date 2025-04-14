// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.columns

import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AttachDialogDefaultColumnsLayout : AttachDialogColumnsLayout() {

  companion object {
    const val EXECUTABLE_CELL_KEY = "AttachDialogExecutable"
    const val PID_CELL_KEY = "AttachDialogProcessId"
    const val USER_CELL_KEY = "AttachDialogProcessOwner"
    const val DEBUGGERS_CELL_KEY = "AttachDialogDebuggers"
    const val COMMAND_LINE_CELL_KEY = "AttachDialogCommandLine"
  }

  private val columnInfos = listOf(
    AttachDialogColumnInfo(EXECUTABLE_CELL_KEY, ExecutableCell::class.java, XDebuggerBundle.message("xdebugger.attach.executable.column.name"), JBUI.scale(300)),
    AttachDialogColumnInfo(PID_CELL_KEY, PidCell::class.java, XDebuggerBundle.message("xdebugger.attach.pid.column.name"), JBUI.scale(80)),
    AttachDialogColumnInfo(USER_CELL_KEY, UserCell::class.java, XDebuggerBundle.message("xdebugger.attach.user.column.name"),
                             JBUI.scale(70)),
      AttachDialogColumnInfo(DEBUGGERS_CELL_KEY, DebuggersCell::class.java, XDebuggerBundle.message("xdebugger.attach.debuggers.column.name"), JBUI.scale(140)),
      AttachDialogColumnInfo(COMMAND_LINE_CELL_KEY, CommandLineCell::class.java, XDebuggerBundle.message("xdebugger.attach.command.line.column.name"), JBUI.scale(380)),
    )


  override fun getColumnInfos(): List<AttachDialogColumnInfo> = columnInfos

  override fun createCell(
    columnIndex: Int,
    node: AttachDialogProcessNode,
    filters: AttachToProcessElementsFilters,
    isInsideTree: Boolean): AttachTableCell {
    return when (columnIndex) {
      0 -> ExecutableCell(node, filters, this, !isInsideTree)
      1 -> PidCell(node, filters, this)
      2 -> UserCell(node, filters, this)
      3 -> DebuggersCell(node, filters, this)
      4 -> CommandLineCell(node, filters, this)
      else -> throw IllegalStateException("Unexpected column number: $columnIndex")
    }
  }
}
