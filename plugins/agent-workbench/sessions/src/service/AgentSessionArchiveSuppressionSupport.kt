// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.normalizeArchiveThreadTarget
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
internal class AgentSessionArchiveTransitionSuppressions {
  private val lock = Any()
  private val activeSuppressions = LinkedHashSet<ArchiveThreadTarget>()
  private val archivedSuppressions = LinkedHashSet<ArchiveThreadTarget>()

  fun suppressActive(target: ArchiveThreadTarget) {
    synchronized(lock) {
      activeSuppressions.add(normalizeArchiveThreadTarget(target))
    }
  }

  fun unsuppressActive(target: ArchiveThreadTarget) {
    synchronized(lock) {
      activeSuppressions.remove(normalizeArchiveThreadTarget(target))
    }
  }

  fun suppressArchived(target: ArchiveThreadTarget) {
    synchronized(lock) {
      archivedSuppressions.add(normalizeArchiveThreadTarget(target))
    }
  }

  fun applyActiveAuthoritative(path: String, provider: AgentSessionProvider, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    return apply(
      path = path,
      provider = provider,
      threads = threads,
      direction = SuppressionDirection.ACTIVE,
      reconcileAbsent = true,
    )
  }

  fun filterActive(path: String, provider: AgentSessionProvider, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    return apply(
      path = path,
      provider = provider,
      threads = threads,
      direction = SuppressionDirection.ACTIVE,
      reconcileAbsent = false,
    )
  }

  fun filterActiveSnapshot(path: String, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    return apply(
      path = path,
      provider = null,
      threads = threads,
      direction = SuppressionDirection.ACTIVE,
      reconcileAbsent = false,
    )
  }

  fun applyArchivedAuthoritative(
    path: String,
    provider: AgentSessionProvider,
    threads: List<AgentSessionThread>,
  ): List<AgentSessionThread> {
    return apply(
      path = path,
      provider = provider,
      threads = threads,
      direction = SuppressionDirection.ARCHIVED,
      reconcileAbsent = true,
    )
  }

  fun filterArchivedSnapshot(path: String, threads: List<AgentSessionThread>): List<AgentSessionThread> {
    return apply(
      path = path,
      provider = null,
      threads = threads,
      direction = SuppressionDirection.ARCHIVED,
      reconcileAbsent = false,
    )
  }

  private fun apply(
    path: String,
    provider: AgentSessionProvider?,
    threads: List<AgentSessionThread>,
    direction: SuppressionDirection,
    reconcileAbsent: Boolean,
  ): List<AgentSessionThread> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val applicableSuppressions = synchronized(lock) {
      direction.suppressions()
        .asSequence()
        .filter { suppression -> suppression.path == normalizedPath && (provider == null || suppression.provider == provider) }
        .toList()
    }
    if (reconcileAbsent) {
      reconcileAbsentSuppressions(direction = direction, applicableSuppressions = applicableSuppressions, threads = threads)
    }
    return apply(threads, applicableSuppressions)
  }

  private fun reconcileAbsentSuppressions(
    direction: SuppressionDirection,
    applicableSuppressions: List<ArchiveThreadTarget>,
    threads: List<AgentSessionThread>,
  ) {
    if (applicableSuppressions.isEmpty()) {
      return
    }
    val absentSuppressions = applicableSuppressions.filterNot { suppression -> threads.containsSuppressionTarget(suppression) }
    if (absentSuppressions.isEmpty()) {
      return
    }
    val absentSuppressionSet = absentSuppressions.toSet()
    synchronized(lock) {
      direction.suppressions().removeAll(absentSuppressionSet)
    }
  }

  private fun SuppressionDirection.suppressions(): LinkedHashSet<ArchiveThreadTarget> {
    return when (this) {
      SuppressionDirection.ACTIVE -> activeSuppressions
      SuppressionDirection.ARCHIVED -> archivedSuppressions
    }
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

  private enum class SuppressionDirection {
    ACTIVE,
    ARCHIVED,
  }
}

private fun List<AgentSessionThread>.containsSuppressionTarget(target: ArchiveThreadTarget): Boolean {
  return when (target) {
    is ArchiveThreadTarget.Thread -> any { thread -> thread.provider == target.provider && thread.id == target.threadId }
    is ArchiveThreadTarget.SubAgent -> any { thread ->
      thread.provider == target.provider && thread.id == target.parentThreadId && thread.subAgents.any { subAgent -> subAgent.id == target.subAgentId }
    }
  }
}
