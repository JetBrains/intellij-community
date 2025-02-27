// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.execution.actions.ChooseDebugConfigurationPopupAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.event.KeyEvent

open class ResumeAction : XDebuggerActionBase(), DumbAware {
  override fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project
    if (project == null) return false

    val session = DebuggerUIUtil.getSession(e) as XDebugSessionImpl?
    if (session != null && !session.isStopped) {
      return !session.isReadOnly && session.isPaused
    }
    // disable visual representation but leave the shortcut action enabled
    return e.inputEvent is KeyEvent
  }

  override fun isHidden(event: AnActionEvent): Boolean {
    if (!PauseAction.isPauseResumeMerged()) {
      return super.isHidden(event)
    }
    return super.isHidden(event) || !isEnabled(event)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!performWithHandler(e)) {
      val project = getEventProject(e)
      if (project != null && !isDumb(project)) {
        ChooseDebugConfigurationPopupAction().actionPerformed(e)
      }
    }
  }

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return debuggerSupport.resumeActionHandler
  }
}
