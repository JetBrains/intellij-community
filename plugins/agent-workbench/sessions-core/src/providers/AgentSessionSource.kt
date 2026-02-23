// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AgentSessionSource {
  val provider: AgentSessionProvider
  val canReportExactThreadCount: Boolean
    get() = true

  val supportsUpdates: Boolean
    get() = false

  val updates: Flow<Unit>
    get() = emptyFlow()

  suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread>

  suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread>

  /**
   * Prefetch threads for multiple paths in a single backend call.
   * Returns a map of path to threads. Empty map means no prefetch (use per-path calls).
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> = emptyMap()
}
