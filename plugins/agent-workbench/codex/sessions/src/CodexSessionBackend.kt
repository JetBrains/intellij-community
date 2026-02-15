// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.openapi.project.Project

data class CodexBackendThread(
  val thread: CodexThread,
  val activity: CodexSessionActivity = CodexSessionActivity.READY,
)

enum class CodexSessionActivity {
  UNREAD,
  REVIEWING,
  PROCESSING,
  READY,
}

interface CodexSessionBackend {
  suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread>

  /**
   * Prefetch threads for multiple paths in a single backend call.
   * Returns a map of path to threads. Empty map means no prefetch (use per-path calls).
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> = emptyMap()
}

