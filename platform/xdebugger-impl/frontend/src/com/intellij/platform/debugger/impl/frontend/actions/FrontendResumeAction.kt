// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.actions.PauseAction
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import java.awt.event.KeyEvent

private class FrontendResumeAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    if (PauseAction.isPauseResumeMerged()) {
      e.presentation.isEnabledAndVisible = isEnabled(e)
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = isEnabled(e)
  }

  private fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project
    if (project == null) {
      return false
    }
    val session = e.frontendDebuggerSession
    if (session != null && !session.isStopped) {
      return !session.isReadOnly && session.isPaused
    }
    // disable visual representation but leave the shortcut action enabled
    return e.inputEvent is KeyEvent
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