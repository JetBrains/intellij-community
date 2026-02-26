// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal class AgentSessionsSelectThreadInToolWindowAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit
  private val activateSessionsToolWindow: (Project) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    ensureThreadVisible = { path, provider, threadId ->
      service<AgentSessionsService>().ensureThreadVisible(path = path, provider = provider, threadId = threadId)
    }
    activateSessionsToolWindow = { project ->
      ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
    }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit,
    activateSessionsToolWindow: (Project) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.ensureThreadVisible = ensureThreadVisible
    this.activateSessionsToolWindow = activateSessionsToolWindow
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val threadCoordinates = threadCoordinatesFromEditorContext(context) ?: return
    ensureThreadVisible(context.path, threadCoordinates.provider, threadCoordinates.threadId)
    activateSessionsToolWindow(context.project)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    e.presentation.isEnabledAndVisible = context != null && threadCoordinatesFromEditorContext(context) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private data class EditorTabThreadCoordinates(
  val provider: AgentSessionProvider,
  val threadId: String,
)

private fun threadCoordinatesFromEditorContext(context: AgentChatEditorTabActionContext): EditorTabThreadCoordinates? {
  if (context.isPendingThread) {
    return null
  }
  val provider = context.provider ?: return null
  val threadId = context.sessionId.takeIf { it.isNotBlank() } ?: return null
  return EditorTabThreadCoordinates(provider = provider, threadId = threadId)
}
