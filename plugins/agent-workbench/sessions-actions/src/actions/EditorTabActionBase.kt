// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.resolveAgentThreadViewEditorTabActionContext
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

abstract class AgentSessionsEditorTabActionBase(
  private val resolveContext: (AnActionEvent) -> AgentThreadViewEditorTabActionContext? =
    ::resolveAgentThreadViewEditorTabActionContext,
) : DumbAwareAction() {
  protected fun resolveEditorTabContext(e: AnActionEvent): AgentThreadViewEditorTabActionContext? {
    return resolveContext(e)
  }

  protected fun resolveEditorTabContextOrHide(e: AnActionEvent): AgentThreadViewEditorTabActionContext? {
    val context = resolveEditorTabContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return null
    }
    return context
  }

  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
