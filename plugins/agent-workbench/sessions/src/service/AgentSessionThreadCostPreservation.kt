// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread

internal fun preserveThreadCosts(
  existingThreads: List<AgentSessionThread>,
  newThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  if (existingThreads.isEmpty() || newThreads.isEmpty()) {
    return newThreads
  }

  val existingByKey = existingThreads.associateBy { thread -> ThreadCostIdentity(thread.provider, thread.id) }
  var changed = false
  val updatedThreads = newThreads.map { thread ->
    val existing = existingByKey[ThreadCostIdentity(thread.provider, thread.id)]
    if (existing == null || thread.cost != null || existing.updatedAt != thread.updatedAt || existing.cost == null) {
      thread
    }
    else {
      changed = true
      thread.copy(cost = existing.cost)
    }
  }
  return if (changed) updatedThreads else newThreads
}

internal fun preserveThreadCost(existing: AgentSessionThread, updated: AgentSessionThread): AgentSessionThread {
  if (updated.cost != null || existing.updatedAt != updated.updatedAt || existing.cost == null) {
    return updated
  }
  return updated.copy(cost = existing.cost)
}

private data class ThreadCostIdentity(
  val provider: AgentSessionProvider,
  val threadId: String,
)
