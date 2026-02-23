// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal data class AgentChatEditorTabActionContext(
  val project: Project,
  val path: String,
  val threadIdentity: String,
  val threadId: String,
)

internal data class AgentChatThreadEditorTabActionContext(
  val project: Project,
  val path: String,
  val provider: AgentSessionProvider,
  val threadId: String,
  val thread: AgentSessionThread,
)

internal fun resolveAgentChatEditorTabActionContext(event: AnActionEvent): AgentChatEditorTabActionContext? {
  val project = event.project ?: return null
  val selectedChatTab = project.service<AgentChatTabSelectionService>().selectedChatTab.value ?: return null
  return AgentChatEditorTabActionContext(
    project = project,
    path = normalizeAgentWorkbenchPath(selectedChatTab.projectPath),
    threadIdentity = selectedChatTab.threadIdentity,
    threadId = selectedChatTab.threadId,
  )
}

internal fun resolveAgentChatThreadEditorTabActionContext(event: AnActionEvent): AgentChatThreadEditorTabActionContext? {
  val editorContext = resolveAgentChatEditorTabActionContext(event) ?: return null
  return toThreadEditorTabActionContext(editorContext)
}

internal fun toThreadEditorTabActionContext(editorContext: AgentChatEditorTabActionContext): AgentChatThreadEditorTabActionContext? {
  if (isAgentSessionNewIdentity(editorContext.threadIdentity)) {
    return null
  }

  val identity = parseAgentSessionIdentity(editorContext.threadIdentity) ?: return null
  if (identity.sessionId.isBlank()) {
    return null
  }

  val title = editorContext.threadId.takeIf { it.isNotBlank() } ?: identity.sessionId
  val thread = AgentSessionThread(
    id = identity.sessionId,
    title = title,
    updatedAt = 0L,
    archived = false,
    provider = identity.provider,
  )
  return AgentChatThreadEditorTabActionContext(
    project = editorContext.project,
    path = editorContext.path,
    provider = identity.provider,
    threadId = identity.sessionId,
    thread = thread,
  )
}
