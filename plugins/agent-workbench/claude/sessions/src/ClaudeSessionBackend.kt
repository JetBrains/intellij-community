// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.openapi.project.Project

data class ClaudeBackendThread(
  val id: String,
  val title: String,
  val updatedAt: Long,
  val gitBranch: String? = null,
)

interface ClaudeSessionBackend {
  suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread>
}

