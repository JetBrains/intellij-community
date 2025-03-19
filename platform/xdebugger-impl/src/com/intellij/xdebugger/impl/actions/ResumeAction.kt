// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.event.KeyEvent

// This action should be migrated to FrontendResumeAction when debugger toolwindow won't be LUXed in Remote Dev
@Deprecated("Don't use this action directly, implement your own instead by using XDebugSession.resume")
open class ResumeAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabled = false
      return
    }
    val session = DebuggerUIUtil.getSession(e) as XDebugSessionImpl?
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
    val session = DebuggerUIUtil.getSession(e) ?: return
    performDebuggerAction(e) {
      session.resume()
    }
  }
}