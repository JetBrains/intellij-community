// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationKind
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.resolveCodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.codex.sessions.backend.toCodexActivitySignals
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<CodexAppServerRefreshHintsProvider>()
private const val REFRESH_HINT_PARALLELISM = 8

internal class CodexAppServerRefreshHintsProvider(
  private val readThreadActivitySnapshot: suspend (String) -> CodexThreadActivitySnapshot?,
  notifications: Flow<CodexAppServerNotification>,
) : CodexRefreshHintsProvider {
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
      .filter { notification -> notification.kind in directStatusUpdateKinds },
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
    if (paths.isEmpty() || knownThreadIdsByPath.isEmpty()) {
      return emptyMap()
    }

    val candidateThreadIds = linkedSetOf<String>()
    for (path in paths) {
      knownThreadIdsByPath[path]
        .orEmpty()
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(::isPendingThreadId)
        .forEach(candidateThreadIds::add)
    }

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
    var resolvedThreadCount = 0
    for (path in paths) {
      val idsForPath = knownThreadIdsByPath[path].orEmpty()
      if (idsForPath.isEmpty()) {
        continue
      }

      val activityByThreadId = LinkedHashMap<String, com.intellij.agent.workbench.common.AgentThreadActivity>()
      for (threadId in idsForPath) {
        val snapshot = snapshotsByThreadId[threadId] ?: continue
        activityByThreadId[threadId] = resolveCodexSessionActivity(snapshot.toCodexActivitySignals()).toAgentThreadActivity()
        resolvedThreadCount += 1
      }

      if (activityByThreadId.isEmpty()) {
        continue
      }
      hintsByPath[path] = AgentSessionRefreshHints(activityByThreadId = activityByThreadId)
    }

    LOG.debug {
      "Codex app-server activity prefetch finished (pathsWithHints=${hintsByPath.size}, resolvedThreads=$resolvedThreadCount)"
    }
    return hintsByPath
  }
}

private const val APP_SERVER_OUTPUT_NOTIFICATION_DEBOUNCE_MS = 250L

private fun isPendingThreadId(threadId: String): Boolean {
  return threadId.startsWith("new-")
}
