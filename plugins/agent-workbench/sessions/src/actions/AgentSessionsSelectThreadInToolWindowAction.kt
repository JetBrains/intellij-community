// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.service.AgentSessionsService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal class AgentSessionsSelectThreadInToolWindowAction @JvmOverloads constructor(
  private val ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit = { path, provider, threadId ->
    service<AgentSessionsService>().ensureThreadVisible(path = path, provider = provider, threadId = threadId)
  },
  private val activateSessionsToolWindow: (Project) -> Unit = { project ->
    ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
  },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return
    ensureThreadVisible(context.path, threadCoordinates.provider, threadCoordinates.threadId)
    activateSessionsToolWindow(context.project)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    e.presentation.isEnabledAndVisible = context != null && resolveAgentSessionsEditorTabThreadCoordinates(context) != null
  }
}
