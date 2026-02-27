// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeCommand

internal data class AgentSessionChatOpenPayload(
  @JvmField val threadIdentity: String,
  @JvmField val shellCommand: List<String>,
  @JvmField val runtimeThreadId: String,
  @JvmField val threadTitle: String,
  @JvmField val subAgentId: String?,
)

internal fun resolveAgentSessionChatOpenPayload(
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  shellCommandOverride: List<String>?,
): AgentSessionChatOpenPayload {
  val threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
  val runtimeThreadId = subAgent?.id ?: thread.id
  val shellCommand = shellCommandOverride ?: buildAgentSessionResumeCommand(provider = thread.provider, sessionId = runtimeThreadId)
  val threadTitle = subAgent?.name?.ifBlank { subAgent.id } ?: thread.title
  return AgentSessionChatOpenPayload(
    threadIdentity = threadIdentity,
    shellCommand = shellCommand,
    runtimeThreadId = runtimeThreadId,
    threadTitle = threadTitle,
    subAgentId = subAgent?.id,
  )
}
