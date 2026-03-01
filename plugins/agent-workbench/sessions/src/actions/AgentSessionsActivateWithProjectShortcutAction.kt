// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

internal class AgentSessionsActivateWithProjectShortcutAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      return
    }

    ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.project?.let(AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject) == true
    if (!enabled) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.setText(AgentSessionsBundle.messagePointer("toolwindow.stripe.agent.workbench.sessions"))
    e.presentation.isEnabledAndVisible = true
  }
}
