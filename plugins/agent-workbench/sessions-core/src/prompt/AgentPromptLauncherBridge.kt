// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AgentPromptLauncherBridge {
  fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult

  fun preferredProvider(): AgentSessionProvider? {
    return null
  }

  fun observeExistingThreads(
    projectPath: String,
    provider: AgentSessionProvider,
  ): Flow<AgentPromptExistingThreadsSnapshot> {
    return flowOf(
      AgentPromptExistingThreadsSnapshot(
        threads = emptyList(),
        isLoading = false,
        hasLoaded = true,
        hasError = false,
      )
    )
  }

  suspend fun refreshExistingThreads(projectPath: String, provider: AgentSessionProvider) {
  }

  fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String? {
    return null
  }

  fun resolveSourceProject(invocationData: AgentPromptInvocationData): Project? {
    return invocationData.project
  }

  fun listWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
    return emptyList()
  }
}

private class AgentPromptLauncherBridgeLog

private val LOG = logger<AgentPromptLauncherBridgeLog>()
private val AGENT_PROMPT_LAUNCHER_BRIDGE_EP: ExtensionPointName<AgentPromptLauncherBridge> =
  ExtensionPointName("com.intellij.agent.workbench.promptLauncher")

object AgentPromptLaunchers {
  fun find(): AgentPromptLauncherBridge? {
    val launchers = try {
      AGENT_PROMPT_LAUNCHER_BRIDGE_EP.extensionList
    }
    catch (t: IllegalStateException) {
      LOG.debug("Prompt launcher bridge EP is unavailable in this context", t)
      return null
    }
    catch (t: IllegalArgumentException) {
      LOG.debug("Prompt launcher bridge EP is unavailable in this context", t)
      return null
    }

    if (launchers.size > 1) {
      LOG.warn("Multiple prompt launchers registered; using first: ${launchers.map { it::class.java.name }}")
    }
    return launchers.firstOrNull()
  }
}
