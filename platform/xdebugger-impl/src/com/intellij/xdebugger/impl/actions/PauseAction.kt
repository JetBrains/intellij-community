// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.performDebuggerActionAsync
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

open class PauseAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    var supported = false
    val session = DebuggerUIUtil.getSessionProxy(e)
    if (session != null) {
      supported = session.isPauseActionSupported
    }
    else {
      DebuggerUIUtil.getSessionData(e)?.apply {
        supported = isPauseSupported
      }
    }
    if (!supported) {
      e.presentation.setEnabledAndVisible(false)
      return
    }
    if (e.project == null || session == null || session.isStopped || session.isPaused) {
      e.presentation.setEnabled(false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(e)
    if (session != null) {
      performDebuggerActionAsync(e) {
        XDebugSessionApi.getInstance().pause(session.id)
      }
    }
  }
}
