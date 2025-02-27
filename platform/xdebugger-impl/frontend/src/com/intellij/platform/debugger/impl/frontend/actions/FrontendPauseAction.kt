// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.actions.PauseAction.isPauseResumeMerged
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi

private class FrontendPauseAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val session = e.frontendDebuggerSession
    if (session == null || !session.isPauseActionSupported) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (isPauseResumeMerged()) {
      e.presentation.isEnabledAndVisible = isEnabled(e)
    }
    else {
      e.presentation.isVisible = true
      e.presentation.isEnabled = isEnabled(e)
    }
  }

  private fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project
    val session = e.frontendDebuggerSession
    return project != null && session != null && !session.isStopped && !session.isPaused
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.frontendDebuggerSession ?: return
    performDebuggerActionAsync(e, updateInlays = true) {
      XDebugSessionApi.getInstance().pause(session.id)
    }
  }
}