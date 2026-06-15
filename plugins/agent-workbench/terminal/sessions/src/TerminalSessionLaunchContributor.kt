// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.terminal.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.components.service

internal class TerminalSessionLaunchContributor(
  private val stateService: TerminalSessionStateService = service(),
) : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != AgentSessionProvider.TERMINAL || sessionId == null) {
      return launchSpec
    }
    val workingDirectory = stateService.readRestoreContext(path = projectPath, threadId = sessionId)
                             ?.workingDirectory
                             ?.takeIf { it.isNotBlank() }
                           ?: return launchSpec
    return launchSpec.copy(workingDirectory = workingDirectory)
  }
}
