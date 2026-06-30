// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.terminal.sessions

// @spec plugins/ij-air/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.components.service

internal class TerminalSessionLaunchContributor(
  private val stateService: TerminalSessionStateService = service(),
) : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    @Suppress("UNUSED_PARAMETER") projectDirectory: String?,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != TERMINAL_AGENT_SESSION_PROVIDER || sessionId == null) {
      return launchSpec
    }
    val workingDirectory = stateService.readRestoreContext(path = projectPath, threadId = sessionId)
                             ?.workingDirectory
                             ?.takeIf { it.isNotBlank() }
                           ?: return launchSpec
    return launchSpec.copy(workingDirectory = workingDirectory)
  }
}
