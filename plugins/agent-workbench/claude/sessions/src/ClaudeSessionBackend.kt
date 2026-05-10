// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
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
)

interface ClaudeSessionBackend {
  suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread>

  suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): ClaudeBackendThreadRefreshResult? = null

  val sessionUpdates: Flow<ClaudeSessionUpdate>
    get() = updates.map { ClaudeSessionUpdate() }

  val updates: Flow<Unit>
    get() = emptyFlow()
}

data class ClaudeSessionUpdate(
  @JvmField val scopedPaths: Set<String>? = null,
  @JvmField val threadIds: Set<String>? = null,
)

data class ClaudeBackendThreadRefreshResult(
  @JvmField val threads: List<ClaudeBackendThread> = emptyList(),
  @JvmField val removedThreadIds: Set<String> = emptySet(),
  @JvmField val isComplete: Boolean = false,
)
