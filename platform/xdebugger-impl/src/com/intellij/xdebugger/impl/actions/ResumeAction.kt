// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.performDebuggerActionAsync
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.event.KeyEvent

open class ResumeAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabled = false
      return
    }
    val session = DebuggerUIUtil.getSessionProxy(e)
    if (session == null) {
      e.presentation.isEnabled = false
      return
    }
    if (!session.isStopped) {
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
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    performDebuggerActionAsync(e) {
      XDebugSessionApi.getInstance().resume(session.id)
    }
  }
}