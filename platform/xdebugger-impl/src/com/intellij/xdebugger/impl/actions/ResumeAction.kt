// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.event.KeyEvent

open class ResumeAction : DumbAwareAction() {
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
    val session = DebuggerUIUtil.getSession(e) as XDebugSessionImpl?
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
    val session = DebuggerUIUtil.getSession(e) ?: return
    performDebuggerAction(e) {
      session.resume()
    }
  }
}