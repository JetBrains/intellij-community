// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

// This action should be migrated to FrontendPauseAction when debugger toolwindow won't be LUXed in Remote Dev
@Deprecated("Don't use this action directly, implement your own instead by using XDebugSession.pause")
open class PauseAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val session = DebuggerUIUtil.getSession(e)
    if (session == null || !(session as XDebugSessionImpl).isPauseActionSupported()) {
      e.getPresentation().setEnabledAndVisible(false)
      return
    }
    val project = e.getProject()
    if (project == null || session.isStopped() || session.isPaused()) {
      e.getPresentation().setEnabled(false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = DebuggerUIUtil.getSession(e)
    if (session != null) {
      XDebuggerUtilImpl.performDebuggerAction(e, Runnable { session.pause() })
    }
  }
}
