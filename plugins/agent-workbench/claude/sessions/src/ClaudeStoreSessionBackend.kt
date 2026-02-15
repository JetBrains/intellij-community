// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.openapi.project.Project

class ClaudeStoreSessionBackend(
  private val store: ClaudeSessionsStore = ClaudeSessionsStore(),
) : ClaudeSessionBackend {
  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<ClaudeBackendThread> {
    return store.listThreads(projectPath = path).map { it.toBackendThread() }
  }
}

private fun ClaudeSessionThread.toBackendThread(): ClaudeBackendThread {
  return ClaudeBackendThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    gitBranch = gitBranch,
  )
}

