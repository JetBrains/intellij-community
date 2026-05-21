// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec

internal class AgentChatOpenTopLevelThreadDispatchService : AgentOpenTopLevelThreadDispatchService {
  override suspend fun dispatchIfPresent(
    projectPath: String,
    provider: AgentSessionProvider,
    threadId: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
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
    )
    return true
  }
}
