// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RunToCursorService {
  fun shouldShowInlay(): Boolean
  fun isAtExecution(file: VirtualFile, line: Int): Boolean
  suspend fun canRunToCursor(editor: Editor, lineNumber: Int): Boolean
}

@ApiStatus.Internal
open class DefaultRunToCursorService(protected val project: Project) : RunToCursorService {
  override fun shouldShowInlay(): Boolean {
    val session = XDebuggerManager.getInstance(project).getCurrentSession() as XDebugSessionImpl?
    return session != null && session.isPaused && !session.isReadOnly
  }

  override fun isAtExecution(file: VirtualFile, line: Int): Boolean {
    val session = XDebuggerManager.getInstance(project).getCurrentSession()
    val position = session?.currentPosition ?: return false
    return position.file == file && position.line == line
  }

  override suspend fun canRunToCursor(editor: Editor, lineNumber: Int): Boolean {
    return readAction {
      val position = XSourcePositionImpl.create(FileDocumentManager.getInstance().getFile(editor.getDocument()), lineNumber)
                          ?: return@readAction false
      val types = XBreakpointUtil.getAvailableLineBreakpointTypes(project, position, editor)
      types.any { it.enabledIcon === AllIcons.Debugger.Db_set_breakpoint }
    }
  }
}