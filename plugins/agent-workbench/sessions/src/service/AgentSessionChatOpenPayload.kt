// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity

internal data class AgentSessionChatOpenPayload(
  @JvmField val threadIdentity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val runtimeThreadId: String,
  @JvmField val threadTitle: String,
  @JvmField val subAgentId: String?,
  @JvmField val initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
)

internal suspend fun resolveAgentSessionChatOpenPayload(
  projectPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  resumeLaunchSpecProvider: (suspend (AgentSessionProvider, String) -> AgentSessionTerminalLaunchSpec)? = null,
): AgentSessionChatOpenPayload {
  val threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
  val runtimeThreadId = subAgent?.id ?: thread.id
  val launchSpec = launchSpecOverride
                   ?: resumeLaunchSpecProvider
                     ?.let { provider ->
                       AgentSessionLaunchSpecs.resolveResume(
                         projectPath = projectPath,
                         provider = thread.provider,
                         sessionId = runtimeThreadId,
                         baseLaunchSpecProvider = provider,
                       )
                     }
                   ?: AgentSessionLaunchSpecs.resolveResume(
                     projectPath = projectPath,
                     provider = thread.provider,
                     sessionId = runtimeThreadId,
                   )
  val threadTitle = subAgent?.name?.ifBlank { subAgent.id } ?: thread.title
  return AgentSessionChatOpenPayload(
    threadIdentity = threadIdentity,
    launchSpec = launchSpec,
    runtimeThreadId = runtimeThreadId,
    threadTitle = threadTitle,
    subAgentId = subAgent?.id,
    initialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
  )
}
