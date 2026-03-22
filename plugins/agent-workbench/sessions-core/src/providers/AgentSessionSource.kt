// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

data class AgentSessionRebindCandidate(
  @JvmField val threadId: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val activity: AgentThreadActivity,
)

data class AgentSessionRefreshHints(
  @JvmField val rebindCandidates: List<AgentSessionRebindCandidate> = emptyList(),
  @JvmField val activityByThreadId: Map<String, AgentThreadActivity> = emptyMap(),
)

enum class AgentSessionSourceUpdate {
  THREADS_CHANGED,
  HINTS_CHANGED,
}

interface AgentSessionSource {
  val provider: AgentSessionProvider
  val canReportExactThreadCount: Boolean
    get() = true

  val supportsUpdates: Boolean
    get() = false

  val updates: Flow<Unit>
    get() = emptyFlow()

  /**
   * Typed source updates used by the loading coordinator to distinguish
   * backend listing updates from auxiliary hint updates.
   */
  val updateEvents: Flow<AgentSessionSourceUpdate>
    get() = updates.map { AgentSessionSourceUpdate.THREADS_CHANGED }

  suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread>

  suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread>

  /**
   * Prefetch threads for multiple paths in a single backend call.
   * Returns a map of path to threads. Empty map means no prefetch (use per-path calls).
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> = emptyMap()

  /**
   * Optional provider-specific refresh hints used by the loading coordinator.
   *
   * Hints must not add persisted rows directly. They are consumed for
   * pending-tab rebinding and provider-specific status projection.
   */
  suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, AgentSessionRefreshHints> = emptyMap()

  fun markThreadAsRead(threadId: String, updatedAt: Long) {}

  fun setActiveThreadId(threadId: String?) {}
}
