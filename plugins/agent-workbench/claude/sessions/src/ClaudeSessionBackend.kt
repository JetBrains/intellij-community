// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

  val updates: Flow<Unit>
    get() = emptyFlow()
}
