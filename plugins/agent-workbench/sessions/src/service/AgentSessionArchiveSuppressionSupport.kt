// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread

internal class AgentSessionArchiveSuppressionSupport {
  private val lock = Any()
  private val suppressions = LinkedHashSet<ArchiveSuppression>()

  fun suppress(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    synchronized(lock) {
      suppressions.add(ArchiveSuppression(path = normalizedPath, provider = provider, threadId = threadId))
    }
  }

  fun unsuppress(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    synchronized(lock) {
      suppressions.remove(ArchiveSuppression(path = normalizedPath, provider = provider, threadId = threadId))
    }
  }

  fun apply(path: String, provider: AgentSessionProvider, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val suppressedThreadIds = synchronized(lock) {
      suppressions.asSequence()
        .filter { suppression -> suppression.path == normalizedPath && suppression.provider == provider }
        .map { suppression -> suppression.threadId }
        .toHashSet()
    }
    if (suppressedThreadIds.isEmpty()) {
      return threads
    }
    return threads.filterNot { thread -> thread.id in suppressedThreadIds }
  }
}

private data class ArchiveSuppression(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)
