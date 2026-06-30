// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.terminal.sessions

// @spec plugins/ij-air/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow

internal class TerminalSessionSource(
  private val stateService: TerminalSessionStateService,
) : AgentSessionSource, AgentSessionUpdateSource, AgentSessionArchivedSource {
  override val provider: AgentSessionProvider
    get() = TERMINAL_AGENT_SESSION_PROVIDER

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = stateService.updateEvents

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = false)
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = true)
  }
}
