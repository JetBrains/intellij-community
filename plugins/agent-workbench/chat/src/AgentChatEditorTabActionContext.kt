// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project

data class AgentChatEditorTabActionContext(
  @JvmField val project: Project,
  @JvmField val path: String,
  @JvmField val tabKey: String,
  @JvmField val threadIdentity: String = "",
  @JvmField val threadId: String = "",
  val provider: AgentSessionProvider? = null,
  @JvmField val sessionId: String = "",
  @JvmField val isPendingThread: Boolean = false,
  @JvmField val subAgentId: String? = null,
)

internal data class AgentChatThreadCoordinates(
  val provider: AgentSessionProvider,
  @JvmField val sessionId: String,
  @JvmField val isPending: Boolean,
)

internal fun resolveAgentChatThreadCoordinates(threadIdentity: String): AgentChatThreadCoordinates? {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return null
  }

  val providerId = threadIdentity.substring(0, separator).lowercase()
  val provider = AgentSessionProvider.fromOrNull(providerId) ?: return null
  val sessionId = threadIdentity.substring(separator + 1)
  if (sessionId.isBlank()) {
    return null
  }

  return AgentChatThreadCoordinates(
    provider = provider,
    sessionId = sessionId,
    isPending = sessionId.startsWith("new-"),
  )
}

fun resolveAgentChatEditorTabActionContext(event: AnActionEvent): AgentChatEditorTabActionContext? {
  val project = event.project ?: return null
  val selectedChatFile = event.getData(CommonDataKeys.VIRTUAL_FILE) as? AgentChatVirtualFile ?: return null
  return AgentChatEditorTabActionContext(
    project = project,
    path = normalizeAgentWorkbenchPath(selectedChatFile.projectPath),
    tabKey = selectedChatFile.tabKey,
    threadIdentity = selectedChatFile.threadIdentity,
    threadId = selectedChatFile.threadId,
    provider = selectedChatFile.provider,
    sessionId = selectedChatFile.sessionId,
    isPendingThread = selectedChatFile.isPendingThread,
    subAgentId = selectedChatFile.subAgentId,
  )
}
