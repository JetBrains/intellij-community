// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi

@Suppress("unused") // Should be used when debugger toolwindow won't be LUXed
private class FrontendStepOverAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    updateSuspendedAction(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.frontendDebuggerSession ?: return
    performDebuggerActionAsync(e, updateInlays = true) {
      XDebugSessionApi.getInstance().stepOver(session.id, ignoreBreakpoints = false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}