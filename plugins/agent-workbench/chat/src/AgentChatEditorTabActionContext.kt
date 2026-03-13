// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project

data class AgentChatEditorTabActionContext(
  @JvmField val project: Project,
  @JvmField val path: String,
  @JvmField val tabKey: String,
  @JvmField val threadIdentity: String = "",
  val threadCoordinates: AgentChatThreadCoordinates? = null,
  val sessionActionTarget: SessionActionTarget? = null,
)

data class AgentChatThreadCoordinates(
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
  val threadCoordinates = selectedChatFile.provider
    ?.let { provider ->
      AgentChatThreadCoordinates(
        provider = provider,
        sessionId = selectedChatFile.sessionId,
        isPending = selectedChatFile.isPendingThread,
      )
    }
  return AgentChatEditorTabActionContext(
    project = project,
    path = normalizeAgentWorkbenchPath(selectedChatFile.projectPath),
    tabKey = selectedChatFile.tabKey,
    threadIdentity = selectedChatFile.threadIdentity,
    threadCoordinates = threadCoordinates,
    sessionActionTarget = resolveAgentChatSessionActionTarget(
      path = normalizeAgentWorkbenchPath(selectedChatFile.projectPath),
      threadCoordinates = threadCoordinates,
      threadId = selectedChatFile.threadId,
      threadTitle = selectedChatFile.threadTitle,
      subAgentId = selectedChatFile.subAgentId,
    ),
  )
}

private fun resolveAgentChatSessionActionTarget(
  path: String,
  threadCoordinates: AgentChatThreadCoordinates?,
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
): SessionActionTarget? {
  val coordinates = threadCoordinates ?: return null
  if (coordinates.isPending) {
    return null
  }

  val normalizedSubAgentId = subAgentId?.takeIf { it.isNotBlank() }
  val resolvedThreadId = threadId.takeIf { it.isNotBlank() } ?: normalizedSubAgentId ?: coordinates.sessionId
  if (resolvedThreadId.isBlank()) {
    return null
  }

  return if (normalizedSubAgentId == null) {
    SessionActionTarget.Thread(
      path = path,
      provider = coordinates.provider,
      threadId = resolvedThreadId,
      title = threadTitle,
    )
  }
  else {
    if (resolvedThreadId != normalizedSubAgentId) {
      return null
    }
    SessionActionTarget.SubAgent(
      path = path,
      provider = coordinates.provider,
      parentThreadId = coordinates.sessionId,
      subAgentId = normalizedSubAgentId,
      title = threadTitle,
    )
  }
}
