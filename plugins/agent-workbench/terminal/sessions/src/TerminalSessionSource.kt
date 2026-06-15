// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.terminal.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow

internal class TerminalSessionSource(
  private val stateService: TerminalSessionStateService,
) : AgentSessionSource {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.TERMINAL

  override val supportsUpdates: Boolean
    get() = true

  override val supportsArchivedThreads: Boolean
    get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = stateService.updateEvents

  override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = false)
  }

  override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = false)
  }

  override suspend fun listArchivedThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = true)
  }

  override suspend fun listArchivedThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return stateService.listSessions(path = path, archived = true)
  }
}
