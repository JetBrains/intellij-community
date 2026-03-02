// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionSource(
  private val backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  private val readTracker = ConcurrentHashMap<String, Long>()
  private val readStateUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  @Volatile private var activeThreadId: String? = null

  override val supportsUpdates: Boolean get() = true

  override val updates: Flow<Unit> get() = merge(backend.updates, readStateUpdates)

  override fun setActiveThreadId(threadId: String?) {
    activeThreadId = threadId
  }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val backendThreads = backend.listThreads(path = path, openProject = openProject)
    val currentActiveId = activeThreadId
    for (thread in backendThreads) {
      if (thread.id == currentActiveId || readTracker[thread.id] == MARKED_AS_READ) {
        readTracker[thread.id] = thread.updatedAt
      }
      else {
        readTracker.putIfAbsent(thread.id, thread.updatedAt)
      }
    }
    return backendThreads.map { it.toAgentSessionThread(readTracker) }
  }

  override fun markThreadAsRead(threadId: String, updatedAt: Long) {
    readTracker[threadId] = MARKED_AS_READ
    readStateUpdates.tryEmit(Unit)
  }
}

private const val MARKED_AS_READ = Long.MAX_VALUE

private fun ClaudeBackendThread.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = AgentSessionProvider.CLAUDE,
    originBranch = gitBranch,
    activity = effectiveActivity(readTracker),
  )
}

private fun ClaudeBackendThread.effectiveActivity(readTracker: Map<String, Long>): AgentThreadActivity {
  if (activity == ClaudeSessionActivity.PROCESSING) return AgentThreadActivity.PROCESSING
  val lastSeenAt = readTracker[id]
  if (lastSeenAt == null || updatedAt > lastSeenAt) return AgentThreadActivity.UNREAD
  return AgentThreadActivity.READY
}
