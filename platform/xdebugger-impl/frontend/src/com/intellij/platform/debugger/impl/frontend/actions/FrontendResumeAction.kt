// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import java.awt.event.KeyEvent

@Suppress("unused") // Should be used when debugger toolwindow won't be LUXed
private class FrontendResumeAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabled = false
      return
    }
    val session = e.frontendDebuggerSession
    if (session != null && !session.isStopped) {
      e.presentation.isEnabled = session.isPaused && !session.isReadOnly
    }
    else {
      // disable visual representation but leave the shortcut action enabled
      e.presentation.isEnabled = e.inputEvent is KeyEvent
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.frontendDebuggerSession ?: return
    performDebuggerActionAsync(e, updateInlays = true) {
      XDebugSessionApi.getInstance().resume(session.id)
    }
  }
}