// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationKind
import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.isResponseRequired
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.codex.sessions.backend.toCodexSessionActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
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
  private val activityHintCacheLock = Any()
  private val activityHintCacheByThreadId = LinkedHashMap<String, CachedThreadActivityHint>(256, 0.75f, true)

  private val directStatusUpdateKinds: Set<CodexAppServerNotificationKind> = setOf(
    CodexAppServerNotificationKind.THREAD_STARTED,
    CodexAppServerNotificationKind.THREAD_NAME_UPDATED,
    CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
    CodexAppServerNotificationKind.TURN_STARTED,
    CodexAppServerNotificationKind.TURN_COMPLETED,
  )

  private val outputBurstUpdateKinds: Set<CodexAppServerNotificationKind> = setOf(
    CodexAppServerNotificationKind.COMMAND_EXECUTION_OUTPUT_DELTA,
    CodexAppServerNotificationKind.TERMINAL_INTERACTION,
  )

  private val notificationUpdates: Flow<AgentSessionSourceUpdateEvent> = createNotificationUpdates(notifications)

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = notificationUpdates

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> {
    val normalizedPathToOriginal = normalizePaths(paths)
    if (normalizedPathToOriginal.isEmpty()) {
      return emptyMap()
    }

    val normalizedRefreshThreadSeedsByPath = normalizeRefreshThreadSeedsByPath(refreshThreadSeedsByPath)
    val normalizedKnownThreadIdsByPath = extractThreadIdsByPath(normalizedRefreshThreadSeedsByPath)
    val startedThreadHintsByNormalizedPath = collectStartedThreadHints(
      paths = normalizedPathToOriginal.keys,
      knownThreadIdsByPath = normalizedKnownThreadIdsByPath,
    )

    val candidateThreadIds = collectCandidateThreadIds(
      paths = normalizedPathToOriginal.keys,
      refreshThreadSeedsByPath = normalizedRefreshThreadSeedsByPath,
      startedThreadHintsByPath = startedThreadHintsByNormalizedPath,
      hasCachedActivityHint = ::hasCachedActivityHint,
    )

    LOG.debug {
      "Codex app-server activity prefetch started (paths=${paths.size}, candidateThreadIds=${candidateThreadIds.size})"
    }

    val snapshotsByThreadId = if (candidateThreadIds.isEmpty()) {
      emptyMap()
    }
    else {
      val semaphore = Semaphore(REFRESH_HINT_PARALLELISM)
      coroutineScope {
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
    }

    val hintsByPath = LinkedHashMap<String, CodexRefreshHints>()
    var resolvedActivityThreadCount = 0
    var rebindCandidateCount = 0
    for ((normalizedPath, originalPath) in normalizedPathToOriginal) {
      val refreshThreadSeedsForPath = normalizedRefreshThreadSeedsByPath[normalizedPath].orEmpty()
      val activityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>()
      for (refreshThreadSeed in refreshThreadSeedsForPath) {
        val threadId = refreshThreadSeed.threadId
        if (isPendingThreadId(threadId)) {
          continue
        }

        val activityHint = findCachedActivityHint(refreshThreadSeed)
                           ?: snapshotsByThreadId[threadId]
                             ?.toRefreshActivityHint()
                             ?.also { hint -> cacheActivityHint(threadId = threadId, hint = hint) }
                           ?: continue
        activityHintsByThreadId[threadId] = activityHint
        resolvedActivityThreadCount += 1
      }

      val rebindCandidates = startedThreadHintsByNormalizedPath[normalizedPath]
        .orEmpty()
        .map { hint -> buildRebindCandidate(hint.startedThread, snapshotsByThreadId[hint.startedThread.id]) }
      rebindCandidateCount += rebindCandidates.size

      if (activityHintsByThreadId.isEmpty() && rebindCandidates.isEmpty()) {
        continue
      }
      hintsByPath[originalPath] = CodexRefreshHints(
        rebindCandidates = rebindCandidates,
        activityHintsByThreadId = activityHintsByThreadId,
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

  private fun toHintUpdateEvent(notification: CodexAppServerNotification): AgentSessionSourceUpdateEvent {
    val threadId = notification.threadId?.takeIf { it.isNotBlank() }
    val startedThreadPath = notification.startedThread?.cwd?.takeIf { it.isNotBlank() }?.let(::normalizeRootPath)
    return when {
      startedThreadPath != null -> AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.HINTS_CHANGED,
        scopedPaths = setOf(startedThreadPath),
      )
      threadId != null -> AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.HINTS_CHANGED,
        threadIds = setOf(threadId),
      )
      else -> AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED)
    }
  }

  private fun createNotificationUpdates(notifications: Flow<CodexAppServerNotification>): Flow<AgentSessionSourceUpdateEvent> = channelFlow {
    val lock = Any()
    val pendingThreadIds = LinkedHashSet<String>()
    var pendingUnscopedUpdate = false
    var flushJob: Job? = null

    suspend fun flushPendingUpdate() {
      val updateEvent = synchronized(lock) {
        flushJob = null
        val threadIds = if (pendingThreadIds.isEmpty()) null else LinkedHashSet(pendingThreadIds)
        pendingThreadIds.clear()
        val emitUnscopedUpdate = pendingUnscopedUpdate
        pendingUnscopedUpdate = false
        when {
          emitUnscopedUpdate || threadIds == null -> AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED)
          else -> AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED, threadIds = threadIds)
        }
      }
      send(updateEvent)
    }

    notifications.collect { notification ->
      when (notification.kind) {
        in directStatusUpdateKinds -> {
          invalidateCachedActivityHint(notification.threadId ?: notification.startedThread?.id)
          recordNotificationThreadHint(notification)
          send(toHintUpdateEvent(notification))
        }
        in outputBurstUpdateKinds -> {
          invalidateCachedActivityHint(notification.threadId)
          val updateEvent = toHintUpdateEvent(notification)
          synchronized(lock) {
            val threadIds = updateEvent.threadIds
            if (threadIds == null) {
              pendingUnscopedUpdate = true
            }
            else {
              pendingThreadIds.addAll(threadIds)
            }
            flushJob?.cancel()
            flushJob = launch {
              delay(APP_SERVER_OUTPUT_NOTIFICATION_DEBOUNCE_MS.milliseconds)
              flushPendingUpdate()
            }
          }
        }
        else -> Unit
      }
    }
  }

  private fun findCachedActivityHint(refreshThreadSeed: AgentSessionRefreshThreadSeed): CodexRefreshActivityHint? {
    if (refreshThreadSeed.forceRefresh) {
      return null
    }

    val updatedAt = refreshThreadSeed.updatedAt
    if (updatedAt == UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT) {
      return null
    }
    synchronized(activityHintCacheLock) {
      val cached = activityHintCacheByThreadId[refreshThreadSeed.threadId] ?: return null
      if (cached.snapshotUpdatedAt != updatedAt) {
        return null
      }
      return cached.refreshHint
    }
  }

  private fun hasCachedActivityHint(refreshThreadSeed: AgentSessionRefreshThreadSeed): Boolean {
    return findCachedActivityHint(refreshThreadSeed) != null
  }

  private fun cacheActivityHint(threadId: String, hint: CodexRefreshActivityHint) {
    synchronized(activityHintCacheLock) {
      activityHintCacheByThreadId[threadId] = CachedThreadActivityHint(
        snapshotUpdatedAt = hint.updatedAt,
        refreshHint = hint,
      )
      trimActivityHintCacheLocked()
    }
  }

  private fun invalidateCachedActivityHint(threadId: String?) {
    val normalizedThreadId = threadId?.trim().orEmpty()
    if (normalizedThreadId.isEmpty()) {
      return
    }

    synchronized(activityHintCacheLock) {
      activityHintCacheByThreadId.remove(normalizedThreadId)
    }
  }

  private fun trimActivityHintCacheLocked() {
    while (activityHintCacheByThreadId.size > MAX_CACHED_ACTIVITY_HINTS) {
      val eldestThreadId = activityHintCacheByThreadId.entries.firstOrNull()?.key ?: return
      activityHintCacheByThreadId.remove(eldestThreadId)
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
  @JvmField val startedThread: CodexAppServerStartedThread,
  @JvmField val recordedAtMs: Long,
)

private data class CachedThreadActivityHint(
  @JvmField val snapshotUpdatedAt: Long,
  @JvmField val refreshHint: CodexRefreshActivityHint,
)

private const val APP_SERVER_OUTPUT_NOTIFICATION_DEBOUNCE_MS = 250L
private const val STARTED_THREAD_HINT_TTL_MS = 120_000L
private const val MAX_UNKNOWN_STARTED_THREADS_PER_PATH = 200
private const val MAX_CACHED_ACTIVITY_HINTS = 2_048

private fun normalizePaths(paths: List<String>): LinkedHashMap<String, String> {
  val normalizedPathToOriginal = LinkedHashMap<String, String>(paths.size)
  for (path in paths) {
    val normalizedPath = normalizePath(path) ?: continue
    normalizedPathToOriginal.putIfAbsent(normalizedPath, path)
  }
  return normalizedPathToOriginal
}

private fun normalizeRefreshThreadSeedsByPath(
  refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
): Map<String, Set<AgentSessionRefreshThreadSeed>> {
  val normalizedRefreshThreadSeedsByPath = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>(refreshThreadSeedsByPath.size)
  for ((path, refreshThreadSeeds) in refreshThreadSeedsByPath) {
    val normalizedPath = normalizePath(path) ?: continue
    normalizedRefreshThreadSeedsByPath[normalizedPath] = normalizeRefreshThreadSeeds(refreshThreadSeeds)
  }
  return normalizedRefreshThreadSeedsByPath
}

private fun extractThreadIdsByPath(
  refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
): Map<String, Set<String>> {
  val threadIdsByPath = LinkedHashMap<String, Set<String>>(refreshThreadSeedsByPath.size)
  for ((path, refreshThreadSeeds) in refreshThreadSeedsByPath) {
    threadIdsByPath[path] = refreshThreadSeeds.asSequence().map { it.threadId }.toCollection(LinkedHashSet())
  }
  return threadIdsByPath
}

private fun collectCandidateThreadIds(
  paths: Set<String>,
  refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  startedThreadHintsByPath: Map<String, List<CachedStartedThreadHint>>,
  hasCachedActivityHint: (AgentSessionRefreshThreadSeed) -> Boolean,
): Set<String> {
  val candidateThreadIds = linkedSetOf<String>()
  for (path in paths) {
    refreshThreadSeedsByPath[path]
      .orEmpty()
      .asSequence()
      .filter { refreshThreadSeed ->
        !isPendingThreadId(refreshThreadSeed.threadId) && !hasCachedActivityHint(refreshThreadSeed)
      }
      .map { refreshThreadSeed -> refreshThreadSeed.threadId }
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

private fun normalizeRefreshThreadSeeds(refreshThreadSeeds: Set<AgentSessionRefreshThreadSeed>): Set<AgentSessionRefreshThreadSeed> {
  val normalizedRefreshThreadSeeds = LinkedHashMap<String, AgentSessionRefreshThreadSeed>(refreshThreadSeeds.size)
  refreshThreadSeeds
    .asSequence()
    .forEach { refreshThreadSeed ->
      val normalizedThreadId = refreshThreadSeed.threadId.trim()
      if (normalizedThreadId.isEmpty()) {
        return@forEach
      }

      val existingSeed = normalizedRefreshThreadSeeds[normalizedThreadId]
      normalizedRefreshThreadSeeds[normalizedThreadId] = if (existingSeed == null) {
        refreshThreadSeed.copy(threadId = normalizedThreadId)
      }
      else {
        AgentSessionRefreshThreadSeed(
          threadId = normalizedThreadId,
          updatedAt = maxKnownUpdatedAt(existingSeed.updatedAt, refreshThreadSeed.updatedAt),
          forceRefresh = existingSeed.forceRefresh || refreshThreadSeed.forceRefresh,
        )
      }
    }
  return LinkedHashSet(normalizedRefreshThreadSeeds.values)
}

private fun maxKnownUpdatedAt(left: Long, right: Long): Long {
  return when {
    left == UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT -> right
    right == UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT -> left
    else -> maxOf(left, right)
  }
}

private fun buildRebindCandidate(
  startedThread: CodexAppServerStartedThread,
  snapshot: CodexThreadActivitySnapshot?,
): AgentSessionRebindCandidate {
  val activity = (snapshot?.toCodexSessionActivity() ?: startedThread.toCodexSessionActivity()).toAgentThreadActivity()
  val updatedAt = snapshot?.updatedAt ?: startedThread.updatedAt
  return AgentSessionRebindCandidate(
    threadId = startedThread.id,
    title = startedThread.title,
    updatedAt = updatedAt,
    activity = activity,
  )
}

private fun CodexThreadActivitySnapshot.toRefreshActivityHint(): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = toCodexSessionActivity().toAgentThreadActivity(),
    updatedAt = updatedAt,
    responseRequired = activeFlags.isResponseRequired(),
  )
}

private fun isPendingThreadId(threadId: String): Boolean {
  return threadId.startsWith("new-")
}
