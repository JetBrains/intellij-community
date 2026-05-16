// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
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
    launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  ): AgentSessionTerminalLaunchSpec {
    return resolveResume(
      projectPath = projectPath,
      provider = provider,
      sessionId = sessionId,
      launchMode = launchMode,
      baseLaunchSpecProvider = ::buildDefaultResumeLaunchSpec,
    )
  }

  suspend fun resolveResume(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String,
    launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    baseLaunchSpecProvider: suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val baseLaunchSpec = runCatching {
      baseLaunchSpecProvider(provider, sessionId, launchMode)
    }.getOrElse { t ->
      LOG.warn(
        "Failed to build base resume launch spec for ${provider.value}:$sessionId; falling back to default command",
        t,
      )
      fallbackResumeLaunchSpec(provider, sessionId)
    }
    val augmented = augment(projectPath = projectPath, provider = provider, launchSpec = baseLaunchSpec)
    return AgentSessionLaunchContributors.applyAll(
      projectPath = projectPath,
      provider = provider,
      sessionId = sessionId,
      launchSpec = augmented,
    )
  }
}

private suspend fun buildDefaultResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
  launchMode: AgentSessionLaunchMode,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionProviders.find(provider)?.buildResumeLaunchSpec(sessionId, launchMode)
         ?: fallbackResumeLaunchSpec(provider, sessionId)
}

private fun fallbackResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionTerminalLaunchSpec(command = listOf(provider.value, "resume", sessionId))
}
