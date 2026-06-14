// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class CodexBackendThread(
  @JvmField val thread: CodexThread,
  @JvmField val activity: CodexSessionActivity = CodexSessionActivity.READY,
  @JvmField val requiresResponse: Boolean = false,
  @JvmField val summaryActivity: CodexSessionActivity? = activity,
  @JvmField val subAgentActivitiesById: Map<String, CodexSessionActivity> = emptyMap(),
  @JvmField val usageSnapshots: List<AgentSessionUsageSnapshot> = emptyList(),
  @JvmField val hasExplicitTitle: Boolean = true,
)

data class CodexBackendThreadRefreshResult(
  @JvmField val threads: List<CodexBackendThread> = emptyList(),
  @JvmField val removedThreadIds: Set<String> = emptySet(),
  @JvmField val isComplete: Boolean = false,
)

enum class CodexSessionActivity {
  NEEDS_INPUT,
  UNREAD,
  REVIEWING,
  PROCESSING,
  READY,
}

interface CodexSessionBackend {
  suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread>

  suspend fun listArchivedThreads(path: String, openProject: Project?): List<CodexBackendThread> = emptyList()

  suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): CodexBackendThreadRefreshResult? = null

  val updates: Flow<Unit>
    get() = emptyFlow()

  /**
   * Prefetch threads for multiple paths in a single backend call.
   * Returns a map of path to threads. Empty map means no prefetch (use per-path calls).
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> = emptyMap()
}
