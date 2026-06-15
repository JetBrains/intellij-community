// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchPlanner
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
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  resumeLaunchSpecProvider: (suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec)? = null,
): AgentSessionChatOpenPayload {
  val threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
  val runtimeThreadId = subAgent?.id ?: thread.id
  val launchSpec = launchSpecOverride
                   ?: AgentSessionLaunchPlanner.plan(
                     intent = AgentSessionLaunchIntent(
                       projectPath = projectPath,
                       provider = thread.provider,
                       operation = AgentSessionLaunchOperation.RESUME,
                       sessionId = runtimeThreadId,
                       launchMode = launchMode,
                       generationSettings = generationSettings,
                     ),
                     resumeLaunchSpecProvider = resumeLaunchSpecProvider,
                   ).launchSpec
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
