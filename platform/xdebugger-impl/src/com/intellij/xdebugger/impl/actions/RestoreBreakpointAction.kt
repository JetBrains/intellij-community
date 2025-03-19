// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RestoreBreakpointAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      WriteAction.run<Throwable> { (XDebuggerManagerImpl.getInstance(project).breakpointManager as XBreakpointManagerImpl)
        .restoreLastRemovedBreakpoint()?.navigatable?.navigate(true) }
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
                                         && (XDebuggerManagerImpl.getInstance(
      project).breakpointManager as XBreakpointManagerImpl).canRestoreLastRemovedBreakpoint()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}