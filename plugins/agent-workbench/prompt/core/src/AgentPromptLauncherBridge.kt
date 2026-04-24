// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.agent.workbench.common.extensions.OverridableValue
import com.intellij.agent.workbench.common.extensions.SingleExtensionPointResolver
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
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

  fun loadProviderPreferences(): ProviderPreferences {
    return ProviderPreferences()
  }

  fun saveProviderPreferences(preferences: ProviderPreferences) {
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

  data class ProviderPreferences(
    @JvmField val providerId: String? = null,
    @JvmField val launchMode: AgentSessionLaunchMode? = null,
    @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
  )
}

private class AgentPromptLauncherBridgeLog

private val LOG = logger<AgentPromptLauncherBridgeLog>()
private val AGENT_PROMPT_LAUNCHER_BRIDGE_EP: ExtensionPointName<AgentPromptLauncherBridge> =
  ExtensionPointName("com.intellij.agent.workbench.promptLauncher")

private val REGISTERED_PROMPT_LAUNCHER = SingleExtensionPointResolver(
  log = LOG,
  extensionPoint = AGENT_PROMPT_LAUNCHER_BRIDGE_EP,
  unavailableMessage = "Prompt launcher bridge EP is unavailable in this context",
  multipleExtensionsMessage = { launchers ->
    "Multiple prompt launchers registered; using first: ${launchers.map { it::class.java.name }}"
  },
)

object AgentPromptLaunchers {
  private val launcherOverride = OverridableValue<AgentPromptLauncherBridge?> { REGISTERED_PROMPT_LAUNCHER.findFirstOrNull() }

  fun find(): AgentPromptLauncherBridge? {
    return launcherOverride.value()
  }

  @Suppress("unused")
  fun <T> withLauncherForTest(launcher: AgentPromptLauncherBridge?, action: () -> T): T {
    return launcherOverride.withOverride(launcher, action)
  }
}
