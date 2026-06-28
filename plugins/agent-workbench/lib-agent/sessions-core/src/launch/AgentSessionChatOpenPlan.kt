// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.project.Project
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan

data class AgentSessionChatOpenPlan(
    @JvmField val threadIdentity: String,
    @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
    @JvmField val runtimeThreadId: String,
    @JvmField val threadTitle: String,
    @JvmField val subAgentId: String?,
    @JvmField val initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
)

suspend fun resolveAgentSessionChatOpenPlan(
  projectPath: String,
  projectDirectory: String? = null,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  launchTargetId: String? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  project: Project? = null,
  resumeLaunchSpecProvider: (suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec)? = null,
): AgentSessionChatOpenPlan {
  val threadIdentity = buildAgentThreadIdentity(providerId = thread.provider.value, threadId = thread.id)
  val runtimeThreadId = subAgent?.id ?: thread.id
  val launchSpec = launchSpecOverride
                   ?: AgentSessionLaunchPlanner.plan(
                      intent = AgentSessionLaunchIntent(
                        projectPath = projectPath,
                        projectDirectory = projectDirectory,
                        provider = thread.provider,
                        operation = AgentSessionLaunchOperation.RESUME,
                        sessionId = runtimeThreadId,
                        launchMode = launchMode,
                        launchTargetId = launchTargetId,
                        generationSettings = generationSettings,
                      ),
                     project = project,
                     resumeLaunchSpecProvider = resumeLaunchSpecProvider,
                   ).launchSpec
  val threadTitle = subAgent?.name?.ifBlank { subAgent.id } ?: thread.title
    return AgentSessionChatOpenPlan(
        threadIdentity = threadIdentity,
        launchSpec = launchSpec,
        runtimeThreadId = runtimeThreadId,
        threadTitle = threadTitle,
        subAgentId = subAgent?.id,
        initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    )
}
