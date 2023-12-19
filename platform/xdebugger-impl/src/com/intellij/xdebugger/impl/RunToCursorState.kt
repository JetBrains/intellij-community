// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil

interface RunToCursorState {
  fun isApplicable(project: Project): Boolean
  fun showInlay(project: Project): Boolean
  fun isAtExecution(project: Project, file: VirtualFile, line: Int): Boolean
  suspend fun canRunToCursor(project: Project, position: XSourcePosition, editor: Editor): Boolean
}

class DefaultRunToCursorState : RunToCursorState {
  override fun isApplicable(project: Project) = true

  override fun showInlay(project: Project): Boolean {
    val session = XDebuggerManager.getInstance(project).getCurrentSession() as XDebugSessionImpl?
    return session != null && session.isPaused && !session.isReadOnly
  }

  override fun isAtExecution(project: Project, file: VirtualFile, line: Int): Boolean {
    val session = XDebuggerManager.getInstance(project).getCurrentSession()
    val position = session?.currentPosition ?: return false
    return position.file == file && position.line == line
  }

  override suspend fun canRunToCursor(project: Project, position: XSourcePosition, editor: Editor): Boolean {
    return readAction {
      val types = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, editor)
      types.any { it.enabledIcon === AllIcons.Debugger.Db_set_breakpoint }
    }
  }
}