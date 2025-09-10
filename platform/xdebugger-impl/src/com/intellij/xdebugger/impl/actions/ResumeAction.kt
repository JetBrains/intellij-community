// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.execution.actions.ChooseDebugConfigurationPopupAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.debugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.XDebuggerActionsCollector
import com.intellij.xdebugger.impl.performDebuggerActionAsync
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
    // Do nothing when there is no session, so that LUX-ed action on frontend will delegate to the backend action
    if (session == null) {
      // we may trigger the Choose Run Configuration dialog via the action shortcut, the action must be enabled,
      // but in the Run / Debug toolbar it should be disabled when no session. There the action is attached to the concrete process.
      e.presentation.isEnabled = isFromShortcutOrSearch(e)
      return
    }

    if (!session.isStopped) {
      e.presentation.isEnabled = session.isPaused && !session.isReadOnly
    }
    else {
      // disable visual representation but leave the shortcut action enabled
      e.presentation.isEnabled = isFromShortcutOrSearch(e)
    }
  }

  private fun isFromShortcutOrSearch(e: AnActionEvent): Boolean {
    return e.inputEvent is KeyEvent
           || e.place == ActionPlaces.ACTION_SEARCH
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(e)
    if (session == null) {
      getEventProject(e).let {
        XDebuggerActionsCollector.chooseDebugConfigurationOnResume(e)

        ChooseDebugConfigurationPopupAction().actionPerformed(e)
      }
    }
    else {
      performDebuggerActionAsync(e) {
        XDebuggerActionsCollector.sessionResumedOnResume(e)

        XDebugSessionApi.getInstance().resume(session.id)
      }
    }
  }
}