// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.agent.workbench.common.isWorking
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread

internal fun sortAgentSessionThreadsForDisplay(threads: List<AgentSessionThread>): List<AgentSessionThread> =
  threads.sortedWith(compareByDescending<AgentSessionThread> { it.updatedAt }
                       .thenBy { it.provider.value }
                       .thenBy { it.id })

internal fun mergeAgentSessionThreadsForDisplay(
  previousThreads: List<AgentSessionThread>,
  refreshedThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  if (previousThreads.isEmpty() || refreshedThreads.size < 2) {
    return sortAgentSessionThreadsForDisplay(refreshedThreads)
  }

  val sortedThreads = sortAgentSessionThreadsForDisplay(refreshedThreads)
  val refreshedThreadsByKey = sortedThreads.associateBy { it.orderingKey() }
  val stillWorkingThreads = previousThreads.mapNotNull { previousThread ->
    val refreshedThread = refreshedThreadsByKey[previousThread.orderingKey()] ?: return@mapNotNull null
    if (previousThread.activity.isWorking && refreshedThread.activity.isWorking) refreshedThread else null
  }
  if (stillWorkingThreads.isEmpty()) {
    return sortedThreads
  }

  val stillWorkingKeys = stillWorkingThreads.mapTo(HashSet()) { it.orderingKey() }
  val stillWorkingIterator = stillWorkingThreads.iterator()
  return sortedThreads.map { thread ->
    if (stillWorkingKeys.remove(thread.orderingKey())) stillWorkingIterator.next() else thread
  }
}

private fun AgentSessionThread.orderingKey(): AgentSessionThreadOrderingKey =
  AgentSessionThreadOrderingKey(provider, id)

private data class AgentSessionThreadOrderingKey(
  val provider: AgentSessionProvider,
  val id: String,
)
