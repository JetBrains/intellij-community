// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger

private class AgentSessionLaunchSpecsLogCategory

private val LOG = logger<AgentSessionLaunchSpecsLogCategory>()

object AgentSessionLaunchSpecs {
  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionLaunchSpecAugmenters.augment(projectPath = projectPath, provider = provider, launchSpec = launchSpec)
  }

  suspend fun resolveResume(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String,
  ): AgentSessionTerminalLaunchSpec {
    return resolveResume(
      projectPath = projectPath,
      provider = provider,
      sessionId = sessionId,
      baseLaunchSpecProvider = ::buildDefaultResumeLaunchSpec,
    )
  }

  suspend fun resolveResume(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String,
    baseLaunchSpecProvider: suspend (AgentSessionProvider, String) -> AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val baseLaunchSpec = runCatching {
      baseLaunchSpecProvider(provider, sessionId)
    }.getOrElse { t ->
      LOG.warn(
        "Failed to build base resume launch spec for ${provider.value}:$sessionId; falling back to default command",
        t,
      )
      fallbackResumeLaunchSpec(provider, sessionId)
    }
    return augment(projectPath = projectPath, provider = provider, launchSpec = baseLaunchSpec)
  }
}

private suspend fun buildDefaultResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionProviders.find(provider)?.buildResumeLaunchSpec(sessionId)
         ?: fallbackResumeLaunchSpec(provider, sessionId)
}

private fun fallbackResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionTerminalLaunchSpec(command = listOf(provider.value, "resume", sessionId))
}
