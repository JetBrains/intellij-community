// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.serialization.Serializable
import java.math.BigDecimal

internal interface CodexThreadPathIndex {
  fun recordThreads(threads: Iterable<CodexThread>)

  fun entry(threadId: String): CodexThreadPathIndexEntry?

  fun frozenCost(threadId: String, updatedAt: Long): AgentSessionCost?

  fun recordFrozenCost(threadId: String, updatedAt: Long, cost: AgentSessionCost)
}

internal data class CodexThreadPathIndexEntry(
  @JvmField val threadId: String,
  @JvmField val cwd: String?,
  @JvmField val rolloutPath: String?,
  @JvmField val parentThreadId: String?,
  @JvmField val updatedAt: Long,
  @JvmField val frozenCost: AgentSessionCost? = null,
)

@Service(Service.Level.APP)
@State(name = "CodexThreadPathIndex", storages = [Storage("agentWorkbenchCodexThreadPathIndex.xml")])
internal class CodexThreadPathIndexService
  : SerializablePersistentStateComponent<CodexThreadPathIndexService.State>(State()),
    CodexThreadPathIndex {

  override fun recordThreads(threads: Iterable<CodexThread>) {
    val normalizedEntries = normalizeThreadEntries(threads)
    if (normalizedEntries.isEmpty()) {
      return
    }

    var changed = false
    updateState { current ->
      val updatedEntries = current.entriesByThreadId.toMutableMap()
      for ((threadId, nextEntry) in normalizedEntries) {
        val previous = updatedEntries[threadId]
        val mergedEntry = when {
          previous == null -> nextEntry.toState()
          previous.updatedAt == nextEntry.updatedAt -> previous.copy(
            cwd = nextEntry.cwd ?: previous.cwd,
            rolloutPath = nextEntry.rolloutPath ?: previous.rolloutPath,
            parentThreadId = nextEntry.parentThreadId ?: previous.parentThreadId,
          )
          else -> nextEntry.toState()
        }
        if (updatedEntries[threadId] != mergedEntry) {
          updatedEntries[threadId] = mergedEntry
          changed = true
        }
      }
      if (!changed) current else current.copy(entriesByThreadId = updatedEntries)
    }
  }

  override fun entry(threadId: String): CodexThreadPathIndexEntry? {
    val normalizedThreadId = threadId.normalizeThreadId() ?: return null
    return state.entriesByThreadId[normalizedThreadId]?.toEntry(normalizedThreadId)
  }

  override fun frozenCost(threadId: String, updatedAt: Long): AgentSessionCost? {
    val entry = state.entriesByThreadId[threadId.normalizeThreadId()] ?: return null
    if (entry.updatedAt != updatedAt) {
      return null
    }
    return entry.frozenCost?.toCost()
  }

  override fun recordFrozenCost(threadId: String, updatedAt: Long, cost: AgentSessionCost) {
    val normalizedThreadId = threadId.normalizeThreadId()
    if (normalizedThreadId == null) {
      return
    }

    var changed = false
    updateState { current ->
      val previous = current.entriesByThreadId[normalizedThreadId]
      val next = (previous ?: EntryState(updatedAt = updatedAt)).copy(
        updatedAt = updatedAt,
        frozenCost = cost.toState(),
      )
      if (previous == next) {
        current
      }
      else {
        changed = true
        current.copy(entriesByThreadId = current.entriesByThreadId + (normalizedThreadId to next))
      }
    }
    if (!changed) {
      return
    }
  }

  @Serializable
  internal data class State(
    @JvmField val entriesByThreadId: Map<String, EntryState> = emptyMap(),
  )

  @Serializable
  internal data class EntryState(
    @JvmField val cwd: String? = null,
    @JvmField val rolloutPath: String? = null,
    @JvmField val parentThreadId: String? = null,
    @JvmField val updatedAt: Long = 0L,
    @JvmField val frozenCost: FrozenCostState? = null,
  )

  @Serializable
  internal data class FrozenCostState(
    @JvmField val amountUsd: String? = null,
    @JvmField val kind: String,
    @JvmField val matchedModelId: String? = null,
  )
}

internal class InMemoryCodexThreadPathIndex : CodexThreadPathIndex {
  private val entriesByThreadId = LinkedHashMap<String, CodexThreadPathIndexEntry>()

  override fun recordThreads(threads: Iterable<CodexThread>) {
    for ((threadId, nextEntry) in normalizeThreadEntries(threads)) {
      val previous = entriesByThreadId[threadId]
      entriesByThreadId[threadId] = when {
        previous == null -> nextEntry
        previous.updatedAt == nextEntry.updatedAt -> previous.copy(
          cwd = nextEntry.cwd ?: previous.cwd,
          rolloutPath = nextEntry.rolloutPath ?: previous.rolloutPath,
          parentThreadId = nextEntry.parentThreadId ?: previous.parentThreadId,
        )
        else -> nextEntry
      }
    }
  }

  override fun entry(threadId: String): CodexThreadPathIndexEntry? {
    return entriesByThreadId[threadId.normalizeThreadId()]
  }

  override fun frozenCost(threadId: String, updatedAt: Long): AgentSessionCost? {
    val entry = entriesByThreadId[threadId.normalizeThreadId()] ?: return null
    return entry.frozenCost?.takeIf { entry.updatedAt == updatedAt }
  }

  override fun recordFrozenCost(threadId: String, updatedAt: Long, cost: AgentSessionCost) {
    val normalizedThreadId = threadId.normalizeThreadId() ?: return
    val previous = entriesByThreadId[normalizedThreadId]
    entriesByThreadId[normalizedThreadId] = (previous ?: CodexThreadPathIndexEntry(
      threadId = normalizedThreadId,
      cwd = null,
      rolloutPath = null,
      parentThreadId = null,
      updatedAt = updatedAt,
    )).copy(
      updatedAt = updatedAt,
      frozenCost = cost,
    )
  }
}

private fun normalizeThreadEntries(threads: Iterable<CodexThread>): Map<String, CodexThreadPathIndexEntry> {
  val result = LinkedHashMap<String, CodexThreadPathIndexEntry>()
  threads.forEach { thread ->
    val threadId = thread.id.normalizeThreadId() ?: return@forEach
    result[threadId] = CodexThreadPathIndexEntry(
      threadId = threadId,
      cwd = thread.cwd?.normalizeStoredPath(),
      rolloutPath = thread.path?.normalizeStoredPath(),
      parentThreadId = thread.parentThreadId?.normalizeThreadId(),
      updatedAt = thread.updatedAt,
    )
  }
  return result
}

private fun CodexThreadPathIndexService.EntryState.toEntry(threadId: String): CodexThreadPathIndexEntry {
  return CodexThreadPathIndexEntry(
    threadId = threadId,
    cwd = cwd,
    rolloutPath = rolloutPath,
    parentThreadId = parentThreadId,
    updatedAt = updatedAt,
    frozenCost = frozenCost?.toCost(),
  )
}

private fun CodexThreadPathIndexEntry.toState(): CodexThreadPathIndexService.EntryState {
  return CodexThreadPathIndexService.EntryState(
    cwd = cwd,
    rolloutPath = rolloutPath,
    parentThreadId = parentThreadId,
    updatedAt = updatedAt,
    frozenCost = frozenCost?.toState(),
  )
}

private fun AgentSessionCost.toState(): CodexThreadPathIndexService.FrozenCostState {
  return CodexThreadPathIndexService.FrozenCostState(
    amountUsd = amountUsd?.toPlainString(),
    kind = kind.name,
    matchedModelId = matchedModelId,
  )
}

private fun CodexThreadPathIndexService.FrozenCostState.toCost(): AgentSessionCost {
  return AgentSessionCost(
    amountUsd = amountUsd?.let(::BigDecimal),
    kind = AgentSessionCostKind.valueOf(kind),
    matchedModelId = matchedModelId,
  )
}

private fun String?.normalizeThreadId(): String? {
  return this?.trim()?.takeIf(String::isNotEmpty)
}

private fun String.normalizeStoredPath(): String {
  return normalizeRootPath(trim())
}
