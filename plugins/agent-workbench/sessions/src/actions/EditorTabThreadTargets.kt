// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget

internal data class AgentSessionsEditorTabThreadCoordinates(
  val path: String,
  val provider: AgentSessionProvider,
  val parentThreadId: String,
  val threadId: String,
  val subAgentId: String?,
)

internal fun resolveAgentSessionsEditorTabThreadCoordinates(
  context: AgentChatEditorTabActionContext,
): AgentSessionsEditorTabThreadCoordinates? {
  if (context.isPendingThread) {
    return null
  }

  val provider = context.provider ?: return null
  val sessionId = context.sessionId.takeIf { it.isNotBlank() } ?: return null
  val subAgentId = context.subAgentId?.takeIf { it.isNotBlank() }
  if (context.threadIdentity.isNotBlank()) {
    val separator = context.threadIdentity.indexOf(':')
    if (separator <= 0 || separator == context.threadIdentity.lastIndex) {
      return null
    }
    val identityProviderId = context.threadIdentity.substring(0, separator).lowercase()
    if (identityProviderId != provider.value) {
      return null
    }
    val identitySessionId = context.threadIdentity.substring(separator + 1)
    if (identitySessionId.isBlank() || identitySessionId != sessionId || identitySessionId.startsWith("new-")) {
      return null
    }
  }
  val threadId = context.threadId.takeIf { it.isNotBlank() } ?: subAgentId ?: sessionId
  if (subAgentId != null && threadId != subAgentId) {
    return null
  }
  return AgentSessionsEditorTabThreadCoordinates(
    path = context.path,
    provider = provider,
    parentThreadId = sessionId,
    threadId = threadId,
    subAgentId = subAgentId,
  )
}

internal fun resolveArchiveThreadTargetFromEditorTabContext(context: AgentChatEditorTabActionContext): ArchiveThreadTarget? {
  val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return null
  return if (threadCoordinates.subAgentId == null) {
    ArchiveThreadTarget.Thread(
      path = threadCoordinates.path,
      provider = threadCoordinates.provider,
      threadId = threadCoordinates.threadId,
    )
  }
  else {
    ArchiveThreadTarget.SubAgent(
      path = threadCoordinates.path,
      provider = threadCoordinates.provider,
      parentThreadId = threadCoordinates.parentThreadId,
      subAgentId = threadCoordinates.subAgentId,
    )
  }
}
