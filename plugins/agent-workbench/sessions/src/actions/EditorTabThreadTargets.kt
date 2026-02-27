// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget

internal data class AgentSessionsEditorTabThreadCoordinates(
  val path: String,
  val provider: AgentSessionProvider,
  val threadId: String,
)

internal fun resolveAgentSessionsEditorTabThreadCoordinates(
  context: AgentChatEditorTabActionContext,
): AgentSessionsEditorTabThreadCoordinates? {
  if (context.isPendingThread) {
    return null
  }

  val provider = context.provider ?: return null
  val threadId = context.sessionId.takeIf { it.isNotBlank() } ?: return null
  return AgentSessionsEditorTabThreadCoordinates(
    path = context.path,
    provider = provider,
    threadId = threadId,
  )
}

internal fun resolveArchiveThreadTargetFromEditorTabContext(context: AgentChatEditorTabActionContext): ArchiveThreadTarget? {
  val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return null
  return ArchiveThreadTarget(
    path = threadCoordinates.path,
    provider = threadCoordinates.provider,
    threadId = threadCoordinates.threadId,
  )
}
