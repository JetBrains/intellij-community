// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

data class ClaudeBackendThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val archived: Boolean = false,
  @JvmField val updatedAt: Long,
  @JvmField val gitBranch: String? = null,
  @JvmField val activity: ClaudeSessionActivity = ClaudeSessionActivity.READY,
  @JvmField val awaitingAssistantTurn: Boolean = false,
  @JvmField val usageSnapshots: List<AgentSessionUsageSnapshot> = emptyList(),
)

interface ClaudeSessionBackend {
  suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread>

  suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): ClaudeBackendThreadRefreshResult? = null

  val sessionUpdates: Flow<AgentSessionSourceUpdateEvent>
    get() = updates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED) }

  val updates: Flow<Unit>
    get() = emptyFlow()

  fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> = emptyFlow()
}

data class ClaudeBackendThreadRefreshResult(
  @JvmField val threads: List<ClaudeBackendThread> = emptyList(),
  @JvmField val removedThreadIds: Set<String> = emptySet(),
  @JvmField val isComplete: Boolean = false,
)
