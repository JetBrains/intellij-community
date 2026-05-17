// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.createFileBackedSessionChangeFlow
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionActivityHintPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<CodexRolloutSessionBackend>()
private const val CODEX_ROLLOUT_TRAILING_REFRESH_DELAY_MS = 250L

internal class CodexRolloutSessionBackend(
  private val codexHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".codex") },
  rolloutChangeSource: (() -> Flow<FileBackedSessionChangeSet>)? = null,
  private val trailingRefreshDelayMs: Long = CODEX_ROLLOUT_TRAILING_REFRESH_DELAY_MS,
) : CodexSessionBackend {
  private val parser = CodexRolloutParser()
  private val threadIndex = CodexRolloutThreadIndex(codexHomeProvider = codexHomeProvider, parser = parser)
  private val rolloutUpdates: Flow<AgentSessionSourceUpdateEvent> = createUpdatesFlow(
    rolloutChangeSource?.invoke() ?: createWatcherUpdates()
  ).conflate()

  internal val sessionUpdates: Flow<AgentSessionSourceUpdateEvent> = rolloutUpdates

  override val updates: Flow<Unit> = rolloutUpdates.map {}

  private fun createUpdatesFlow(sourceUpdates: Flow<FileBackedSessionChangeSet>): Flow<AgentSessionSourceUpdateEvent> {
    return channelFlow {
      var trailingRefreshJob: Job? = null
      sourceUpdates.collect { changeSet ->
        threadIndex.markDirty(changeSet)
        send(resolveSessionUpdate(changeSet))

        trailingRefreshJob?.cancel()
        trailingRefreshJob = launch {
          delay(trailingRefreshDelayMs.milliseconds)
          send(resolveSessionUpdate(changeSet))
        }
      }
    }
  }

  private suspend fun resolveSessionUpdate(changeSet: FileBackedSessionChangeSet): AgentSessionSourceUpdateEvent {
    return withContext(Dispatchers.IO) {
      buildSessionUpdate(changeSet)
    }
  }

  private fun buildSessionUpdate(changeSet: FileBackedSessionChangeSet): AgentSessionSourceUpdateEvent {
    if (changeSet.requiresFullRescan || changeSet.changedPaths.isEmpty()) {
      LOG.debug {
        "Codex rollout update is unscoped (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedPaths.size})"
      }
      return UNSCOPED_ROLLOUT_SESSION_UPDATE
    }

    val rolloutPaths = changeSet.changedPaths.filter(::isRolloutPath)
    if (rolloutPaths.isEmpty()) {
      LOG.debug { "Codex rollout update is unscoped (no rollout paths in changedPaths=${changeSet.changedPaths.size})" }
      return UNSCOPED_ROLLOUT_SESSION_UPDATE
    }

    val scopedPaths = LinkedHashSet<String>()
    val threadIds = LinkedHashSet<String>()
    val activityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity>()
    val summaryActivityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity?>()
    var failedParses = 0
    for (path in rolloutPaths) {
      val parsedThread = parser.parse(path)
      if (parsedThread == null) {
        failedParses++
        continue
      }
      scopedPaths += parsedThread.normalizedCwd
      threadIds += parsedThread.thread.thread.id
      parsedThread.parentThreadId?.let(threadIds::add)
      if (parsedThread.parentThreadId == null) {
        val threadId = parsedThread.thread.thread.id
        activityHintsByThreadId[threadId] = parsedThread.thread.activity.toAgentThreadActivity()
        summaryActivityHintsByThreadId[threadId] = parsedThread.thread.summaryActivity?.toAgentThreadActivity()
      }
    }

    if (failedParses > 0 || scopedPaths.isEmpty()) {
      LOG.debug {
        "Codex rollout update falls back to unscoped refresh " +
        "(changedRolloutPaths=${rolloutPaths.size}, failedParses=$failedParses, scopedPaths=${scopedPaths.size})"
      }
      return UNSCOPED_ROLLOUT_SESSION_UPDATE
    }

    LOG.debug {
      "Codex rollout update scoped (changedRolloutPaths=${rolloutPaths.size}, scopedPaths=${scopedPaths.size}, threadIds=${threadIds.size})"
    }
    return AgentSessionSourceUpdateEvent(
      type = AgentSessionSourceUpdate.HINTS_CHANGED,
      scopedPaths = scopedPaths,
      threadIds = threadIds.takeIf { it.isNotEmpty() },
      activityHintsByThreadId = activityHintsByThreadId,
      summaryActivityHintsByThreadId = summaryActivityHintsByThreadId,
      activityHintPolicy = AgentSessionActivityHintPolicy.AUTHORITATIVE,
    )
  }

  private fun createWatcherUpdates(): Flow<FileBackedSessionChangeSet> {
    return createFileBackedSessionChangeFlow(
      logger = LOG,
      watcherName = "Codex rollout",
      initContext = { "codexHome=${codexHomeProvider()}" },
      emitInitialRefreshPing = true,
    ) { scope, onChange ->
      CodexRolloutSessionsWatcher(
        codexHomeProvider = codexHomeProvider,
        scope = scope,
        onRolloutChange = onChange,
      )
    }
  }

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    return withContext(Dispatchers.IO) {
      val workingDirectory = resolveProjectDirectoryFromPath(path)
                             ?: return@withContext emptyList()
      val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
      threadIndex.collectByCwd(setOf(cwdFilter))[cwdFilter].orEmpty()
    }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    return withContext(Dispatchers.IO) {
      val pathFilters = resolvePathFilters(paths)
      if (pathFilters.isEmpty()) return@withContext emptyMap()

      val threadsByCwd = threadIndex.collectByCwd(pathFilters.mapTo(HashSet(pathFilters.size)) { (_, cwdFilter) -> cwdFilter })
      pathFilters.associate { (path, cwdFilter) ->
        path to threadsByCwd.get(cwdFilter).orEmpty()
      }
    }
  }
}

private fun resolvePathFilters(paths: List<String>): List<Pair<String, String>> {
  return paths.mapNotNull { path ->
    resolveProjectDirectoryFromPath(path)?.let { directory ->
      path to normalizeRootPath(directory.invariantSeparatorsPathString)
    }
  }
}

private val UNSCOPED_ROLLOUT_SESSION_UPDATE = AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED)

private fun isRolloutPath(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return isRolloutFileName(fileName)
}
