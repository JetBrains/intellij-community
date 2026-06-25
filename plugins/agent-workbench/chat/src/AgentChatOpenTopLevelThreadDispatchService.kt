// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec

internal class AgentChatOpenTopLevelThreadDispatchService : AgentOpenTopLevelThreadDispatchService {
  override suspend fun dispatchIfPresent(
      projectPath: String,
      provider: AgentSessionProvider,
      threadId: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  ): Boolean {
    val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
    val openEntry = collectOpenAgentChatTabsSnapshotOnUi().findOpenTopLevelConcreteEntry(
      normalizedPath = normalizedProjectPath,
      provider = provider,
      threadId = threadId,
    ) ?: return false

    openChat(
      project = openEntry.manager.project,
      projectPath = normalizedProjectPath,
      threadIdentity = openEntry.file.threadIdentity,
      shellCommand = launchSpec.command,
      shellEnvVariables = launchSpec.envVariables,
      threadId = openEntry.file.threadId,
      threadTitle = openEntry.file.threadTitle,
      subAgentId = null,
      threadActivity = openEntry.file.threadActivity,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      startupLaunchSpec = launchSpec,
    )
    return true
  }
}
