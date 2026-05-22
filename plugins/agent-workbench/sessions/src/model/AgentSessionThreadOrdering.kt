// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread

private val AGENT_SESSION_THREAD_DISPLAY_COMPARATOR: Comparator<AgentSessionThread> =
  compareByDescending<AgentSessionThread> { it.updatedAt }
    .thenBy { it.provider.value }
    .thenBy { it.id }

internal fun sortAgentSessionThreadsForDisplay(threads: List<AgentSessionThread>): List<AgentSessionThread> =
  threads.sortedWith(AGENT_SESSION_THREAD_DISPLAY_COMPARATOR)

internal fun mergeAgentSessionThreadsForDisplay(
  previousThreads: List<AgentSessionThread>,
  refreshedThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  if (previousThreads.isEmpty() || refreshedThreads.size < 2) {
    return sortAgentSessionThreadsForDisplay(refreshedThreads)
  }

  val refreshedThreadsByKey = refreshedThreads.associateBy { it.orderingKey() }
  val retainedKeys = HashSet<AgentSessionThreadOrderingKey>()
  val mergedThreads = ArrayList<AgentSessionThread>(refreshedThreadsByKey.size)
  previousThreads.forEach { previousThread ->
    val key = previousThread.orderingKey()
    val refreshedThread = refreshedThreadsByKey[key] ?: return@forEach
    retainedKeys.add(key)
    mergedThreads.add(refreshedThread)
  }
  if (mergedThreads.isEmpty()) {
    return sortAgentSessionThreadsForDisplay(refreshedThreads)
  }
  if (mergedThreads.size == refreshedThreadsByKey.size) {
    return mergedThreads
  }

  val newThreads = refreshedThreadsByKey.values
    .filter { thread -> thread.orderingKey() !in retainedKeys }
    .let(::sortAgentSessionThreadsForDisplay)
  for (thread in newThreads) {
    mergedThreads.insertByDisplayOrder(thread)
  }
  return mergedThreads
}

private fun MutableList<AgentSessionThread>.insertByDisplayOrder(thread: AgentSessionThread) {
  val index = indexOfFirst { existingThread ->
    AGENT_SESSION_THREAD_DISPLAY_COMPARATOR.compare(thread, existingThread) < 0
  }
  if (index < 0) {
    add(thread)
  }
  else {
    add(index, thread)
  }
}

private fun AgentSessionThread.orderingKey(): AgentSessionThreadOrderingKey =
  AgentSessionThreadOrderingKey(provider, id)

private data class AgentSessionThreadOrderingKey(
  val provider: AgentSessionProvider,
  val id: String,
)
