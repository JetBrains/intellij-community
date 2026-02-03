// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class RemoveAllBreakpointsInFileAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (project != null && editor != null) {
      val breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager() as XBreakpointManagerImpl
      val lineBreakpointManager = breakpointManager.lineBreakpointManager
      lineBreakpointManager.getDocumentBreakpoints(editor.getDocument()).forEach {
        breakpointManager.removeBreakpoint(it)
      }
    }
  }
}
