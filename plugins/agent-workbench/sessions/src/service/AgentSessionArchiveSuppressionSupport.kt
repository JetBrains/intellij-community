// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.normalizeArchiveThreadTarget

internal class AgentSessionArchiveSuppressionSupport {
  private val lock = Any()
  private val suppressions = LinkedHashSet<ArchiveThreadTarget>()

  fun suppress(target: ArchiveThreadTarget) {
    synchronized(lock) {
      suppressions.add(normalizeArchiveThreadTarget(target))
    }
  }

  fun unsuppress(target: ArchiveThreadTarget) {
    synchronized(lock) {
      suppressions.remove(normalizeArchiveThreadTarget(target))
    }
  }

  fun apply(path: String, provider: AgentSessionProvider, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val applicableSuppressions = synchronized(lock) {
      suppressions.asSequence()
        .filter { suppression -> suppression.path == normalizedPath && suppression.provider == provider }
        .toList()
    }
    return apply(threads, applicableSuppressions)
  }

  fun apply(path: String, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val applicableSuppressions = synchronized(lock) {
      suppressions.asSequence()
        .filter { suppression -> suppression.path == normalizedPath }
        .toList()
    }
    return apply(threads, applicableSuppressions)
  }

  private fun apply(threads: List<AgentSessionThread>, applicableSuppressions: List<ArchiveThreadTarget>): List<AgentSessionThread> {
    if (applicableSuppressions.isEmpty()) {
      return threads
    }

    val suppressedThreadIdsByProvider = LinkedHashMap<AgentSessionProvider, MutableSet<String>>()
    val suppressedSubAgentIdsByProviderAndParent = LinkedHashMap<AgentSessionProvider, MutableMap<String, MutableSet<String>>>()
    applicableSuppressions.forEach { suppression ->
      when (suppression) {
        is ArchiveThreadTarget.Thread -> {
          suppressedThreadIdsByProvider.getOrPut(suppression.provider) { HashSet() }.add(suppression.threadId)
        }
        is ArchiveThreadTarget.SubAgent -> {
          suppressedSubAgentIdsByProviderAndParent
            .getOrPut(suppression.provider) { LinkedHashMap() }
            .getOrPut(suppression.parentThreadId) { HashSet() }
            .add(suppression.subAgentId)
        }
      }
    }

    var changed = false
    val nextThreads = ArrayList<AgentSessionThread>(threads.size)
    threads.forEach { thread ->
      if (suppressedThreadIdsByProvider[thread.provider]?.contains(thread.id) == true) {
        changed = true
        return@forEach
      }

      val suppressedSubAgentIds = suppressedSubAgentIdsByProviderAndParent[thread.provider]?.get(thread.id)
      if (suppressedSubAgentIds.isNullOrEmpty()) {
        nextThreads.add(thread)
        return@forEach
      }

      val nextSubAgents = thread.subAgents.filterNot { subAgent -> subAgent.id in suppressedSubAgentIds }
      if (nextSubAgents.size != thread.subAgents.size) {
        changed = true
        nextThreads.add(thread.copy(subAgents = nextSubAgents))
      }
      else {
        nextThreads.add(thread)
      }
    }
    return if (changed) nextThreads else threads
  }
}
