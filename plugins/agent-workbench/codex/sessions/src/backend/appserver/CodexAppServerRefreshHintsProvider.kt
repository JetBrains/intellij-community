// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationKind
import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexActivitySignals
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.resolveCodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.codex.sessions.backend.toCodexActivitySignals
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<CodexAppServerRefreshHintsProvider>()
private const val REFRESH_HINT_PARALLELISM = 8

internal class CodexAppServerRefreshHintsProvider(
  private val readThreadActivitySnapshot: suspend (String) -> CodexThreadActivitySnapshot?,
  notifications: Flow<CodexAppServerNotification>,
) : CodexRefreshHintsProvider {
  private val startedThreadHintsLock = Any()
  private val startedThreadHintsByPath = LinkedHashMap<String, LinkedHashMap<String, CachedStartedThreadHint>>()

  private val directStatusUpdateKinds: Set<CodexAppServerNotificationKind> = setOf(
    CodexAppServerNotificationKind.THREAD_STARTED,
    CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
    CodexAppServerNotificationKind.TURN_STARTED,
    CodexAppServerNotificationKind.TURN_COMPLETED,
  )

  private val outputBurstUpdateKinds: Set<CodexAppServerNotificationKind> = setOf(
    CodexAppServerNotificationKind.COMMAND_EXECUTION_OUTPUT_DELTA,
    CodexAppServerNotificationKind.TERMINAL_INTERACTION,
  )

  @OptIn(FlowPreview::class)
  private val notificationUpdates: Flow<Unit> = merge(
    notifications
      .filter { notification -> notification.kind in directStatusUpdateKinds }
      .onEach(::recordNotificationThreadHint),
    notifications
      .filter { notification -> notification.kind in outputBurstUpdateKinds }
      .debounce(APP_SERVER_OUTPUT_NOTIFICATION_DEBOUNCE_MS.milliseconds),
  ).map { }

  override val updates: Flow<Unit>
    get() = notificationUpdates

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, AgentSessionRefreshHints> {
    val normalizedPathToOriginal = normalizePaths(paths)
    if (normalizedPathToOriginal.isEmpty()) {
      return emptyMap()
    }

    val normalizedKnownThreadIdsByPath = normalizeKnownThreadIdsByPath(knownThreadIdsByPath)
    val startedThreadHintsByNormalizedPath = collectStartedThreadHints(
      paths = normalizedPathToOriginal.keys,
      knownThreadIdsByPath = normalizedKnownThreadIdsByPath,
    )

    val candidateThreadIds = collectCandidateThreadIds(
      paths = normalizedPathToOriginal.keys,
      knownThreadIdsByPath = normalizedKnownThreadIdsByPath,
      startedThreadHintsByPath = startedThreadHintsByNormalizedPath,
    )
    if (candidateThreadIds.isEmpty()) {
      return emptyMap()
    }

    LOG.debug {
      "Codex app-server activity prefetch started (paths=${paths.size}, candidateThreadIds=${candidateThreadIds.size})"
    }

    val semaphore = Semaphore(REFRESH_HINT_PARALLELISM)
    val snapshotsByThreadId = coroutineScope {
      candidateThreadIds.map { threadId ->
        async {
          semaphore.withPermit {
            try {
              threadId to readThreadActivitySnapshot(threadId)
            }
            catch (t: Throwable) {
              LOG.warn("Failed to read Codex thread activity snapshot for threadId=$threadId", t)
              threadId to null
            }
          }
        }
      }.awaitAll().associate { it }
    }

    val hintsByPath = LinkedHashMap<String, AgentSessionRefreshHints>()
    var resolvedActivityThreadCount = 0
    var rebindCandidateCount = 0
    for ((normalizedPath, originalPath) in normalizedPathToOriginal) {
      val idsForPath = normalizedKnownThreadIdsByPath[normalizedPath].orEmpty()
      val activityByThreadId = LinkedHashMap<String, AgentThreadActivity>()
      for (threadId in idsForPath) {
        if (isPendingThreadId(threadId)) {
          continue
        }
        val snapshot = snapshotsByThreadId[threadId] ?: continue
        activityByThreadId[threadId] = snapshot.toAgentThreadActivity()
        resolvedActivityThreadCount += 1
      }

      val rebindCandidates = startedThreadHintsByNormalizedPath[normalizedPath]
        .orEmpty()
        .map { hint -> buildRebindCandidate(hint.startedThread, snapshotsByThreadId[hint.startedThread.id]) }
      rebindCandidateCount += rebindCandidates.size

      if (activityByThreadId.isEmpty() && rebindCandidates.isEmpty()) {
        continue
      }
      hintsByPath[originalPath] = AgentSessionRefreshHints(
        rebindCandidates = rebindCandidates,
        activityByThreadId = activityByThreadId,
      )
    }

    LOG.debug {
      "Codex app-server activity prefetch finished " +
      "(pathsWithHints=${hintsByPath.size}, resolvedActivityThreads=$resolvedActivityThreadCount, rebindCandidates=$rebindCandidateCount)"
    }
    return hintsByPath
  }

  private fun recordNotificationThreadHint(notification: CodexAppServerNotification) {
    if (notification.kind != CodexAppServerNotificationKind.THREAD_STARTED) {
      return
    }

    val startedThread = notification.startedThread ?: return
    val threadId = startedThread.id
    val normalizedPath = startedThread.cwd
    val nowMs = System.currentTimeMillis()

    synchronized(startedThreadHintsLock) {
      evictExpiredStartedThreadHintsLocked(nowMs)
      val hintsForPath = startedThreadHintsByPath.getOrPut(normalizedPath) { LinkedHashMap() }
      hintsForPath.remove(threadId)
      hintsForPath[threadId] = CachedStartedThreadHint(startedThread = startedThread, recordedAtMs = nowMs)
      trimStartedThreadHintsLocked(hintsForPath)
      if (hintsForPath.isEmpty()) {
        startedThreadHintsByPath.remove(normalizedPath)
      }
    }
  }

  private fun collectStartedThreadHints(
    paths: Set<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, List<CachedStartedThreadHint>> {
    val nowMs = System.currentTimeMillis()
    synchronized(startedThreadHintsLock) {
      evictExpiredStartedThreadHintsLocked(nowMs)
      val hintsByPath = LinkedHashMap<String, List<CachedStartedThreadHint>>()
      for (path in paths) {
        val hintsForPath = startedThreadHintsByPath[path] ?: continue
        knownThreadIdsByPath[path].orEmpty().forEach(hintsForPath::remove)
        if (hintsForPath.isEmpty()) {
          startedThreadHintsByPath.remove(path)
          continue
        }
        hintsByPath[path] = hintsForPath.values.toList()
      }
      return hintsByPath
    }
  }

  private fun evictExpiredStartedThreadHintsLocked(nowMs: Long) {
    val pathIterator = startedThreadHintsByPath.entries.iterator()
    while (pathIterator.hasNext()) {
      val (_, hintsForPath) = pathIterator.next()
      val hintIterator = hintsForPath.entries.iterator()
      while (hintIterator.hasNext()) {
        val (_, hint) = hintIterator.next()
        if (nowMs - hint.recordedAtMs > STARTED_THREAD_HINT_TTL_MS) {
          hintIterator.remove()
        }
      }
      if (hintsForPath.isEmpty()) {
        pathIterator.remove()
      }
    }
  }

  private fun trimStartedThreadHintsLocked(hintsForPath: LinkedHashMap<String, CachedStartedThreadHint>) {
    while (hintsForPath.size > MAX_UNKNOWN_STARTED_THREADS_PER_PATH) {
      val oldestThreadId = hintsForPath.entries.firstOrNull()?.key ?: return
      hintsForPath.remove(oldestThreadId)
    }
  }
}

private data class CachedStartedThreadHint(
  val startedThread: CodexAppServerStartedThread,
  val recordedAtMs: Long,
)

private const val APP_SERVER_OUTPUT_NOTIFICATION_DEBOUNCE_MS = 250L
private const val STARTED_THREAD_HINT_TTL_MS = 120_000L
private const val MAX_UNKNOWN_STARTED_THREADS_PER_PATH = 200

private fun normalizePaths(paths: List<String>): LinkedHashMap<String, String> {
  val normalizedPathToOriginal = LinkedHashMap<String, String>(paths.size)
  for (path in paths) {
    val normalizedPath = normalizePath(path) ?: continue
    normalizedPathToOriginal.putIfAbsent(normalizedPath, path)
  }
  return normalizedPathToOriginal
}

private fun normalizeKnownThreadIdsByPath(knownThreadIdsByPath: Map<String, Set<String>>): Map<String, Set<String>> {
  val normalizedKnownThreadIdsByPath = LinkedHashMap<String, Set<String>>(knownThreadIdsByPath.size)
  for ((path, threadIds) in knownThreadIdsByPath) {
    val normalizedPath = normalizePath(path) ?: continue
    normalizedKnownThreadIdsByPath[normalizedPath] = normalizeThreadIds(threadIds)
  }
  return normalizedKnownThreadIdsByPath
}

private fun collectCandidateThreadIds(
  paths: Set<String>,
  knownThreadIdsByPath: Map<String, Set<String>>,
  startedThreadHintsByPath: Map<String, List<CachedStartedThreadHint>>,
): Set<String> {
  val candidateThreadIds = linkedSetOf<String>()
  for (path in paths) {
    knownThreadIdsByPath[path]
      .orEmpty()
      .asSequence()
      .filterNot(::isPendingThreadId)
      .forEach(candidateThreadIds::add)
    startedThreadHintsByPath[path]
      .orEmpty()
      .asSequence()
      .map { hint -> hint.startedThread.id }
      .forEach(candidateThreadIds::add)
  }
  return candidateThreadIds
}

private fun normalizePath(path: String): String? {
  return path.trim().takeIf { it.isNotEmpty() }?.let(::normalizeRootPath)
}

private fun normalizeThreadIds(threadIds: Set<String>): Set<String> {
  return threadIds
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toCollection(LinkedHashSet())
}

private fun buildRebindCandidate(
  startedThread: CodexAppServerStartedThread,
  snapshot: CodexThreadActivitySnapshot?,
): AgentSessionRebindCandidate {
  val activity = snapshot?.toAgentThreadActivity() ?: startedThread.toAgentThreadActivity()
  val updatedAt = snapshot?.updatedAt ?: startedThread.updatedAt
  return AgentSessionRebindCandidate(
    threadId = startedThread.id,
    title = startedThread.title,
    updatedAt = updatedAt,
    activity = activity,
  )
}

private fun CodexAppServerStartedThread.toAgentThreadActivity(): AgentThreadActivity {
  return resolveCodexSessionActivity(
    CodexActivitySignals(
      statusKind = statusKind,
      activeFlags = activeFlags.toSet(),
      hasUnreadAssistantMessage = false,
      isReviewing = false,
      hasInProgressTurn = false,
    )
  ).toAgentThreadActivity()
}

private fun CodexThreadActivitySnapshot.toAgentThreadActivity(): AgentThreadActivity {
  return resolveCodexSessionActivity(toCodexActivitySignals()).toAgentThreadActivity()
}

private fun isPendingThreadId(threadId: String): Boolean {
  return threadId.startsWith("new-")
}
