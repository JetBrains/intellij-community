// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.codex.sessions.backend.rollout

// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThreadRefreshResult
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.filewatch.agentWorkbenchImmediateFileChangeFlow
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.createFileBackedSessionChangeFlow
import com.intellij.agent.workbench.json.filebacked.toFileBackedSessionPathKey
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
  private val threadIndex = CodexRolloutThreadIndex(codexHomeProvider = codexHomeProvider, parseRollout = parser::parse)
  private val projectFilesChangedAtByPathKey = HashMap<String, Long>()
  private val projectFilesChangedAtLock = Any()
  private val rolloutUpdates: Flow<AgentSessionSourceUpdateEvent> = createUpdatesFlow(
    rolloutChangeSource?.invoke() ?: createWatcherUpdates()
  ).conflate()

  internal val sessionUpdates: Flow<AgentSessionSourceUpdateEvent> = rolloutUpdates

  override val updates: Flow<Unit> = rolloutUpdates.map {}

  fun activeThreadFileChangeEvents(path: String, threadId: String): Flow<Unit> {
    return flow {
      val files = withContext(Dispatchers.IO) {
        resolveActiveThreadFilePaths(path = path, threadId = threadId)
      }
      LOG.debug {
        "Resolved Codex active rollout files for immediate watch (path=$path, threadId=$threadId, files=${files.size})"
      }
      emitAll(agentWorkbenchImmediateFileChangeFlow(files).map {})
    }
  }

  internal fun resolveActiveThreadFilePaths(path: String, threadId: String): List<Path> {
    return threadIndex.resolveThreadFilePaths(path = path, threadId = threadId)
  }

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
    if (changeSet.requiresFullRescan) {
      LOG.debug {
        "Codex rollout update is unscoped (fullRescan=true, changedPaths=${changeSet.changedPaths.size})"
      }
      return rolloutSessionUpdate(mayHaveChangedProjectFiles = true)
    }

    if (changeSet.changedPaths.isEmpty()) {
      LOG.debug { "Codex rollout update is unscoped (refresh ping)" }
      return rolloutSessionUpdate()
    }

    val rolloutPaths = changeSet.changedPaths.filter(::isRolloutPath)
    if (rolloutPaths.isEmpty()) {
      LOG.debug { "Codex rollout update is unscoped (no rollout paths in changedPaths=${changeSet.changedPaths.size})" }
      return rolloutSessionUpdate()
    }

    val scopedPaths = LinkedHashSet<String>()
    val threadIds = LinkedHashSet<String>()
    val activityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity>()
    val summaryActivityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity?>()
    var mayHaveChangedProjectFiles = false
    var changedProjectFilePaths: LinkedHashSet<String>? = LinkedHashSet()
    var failedParses = 0
    for (path in rolloutPaths) {
      val parsedThread = parser.parse(path)
      if (parsedThread == null) {
        failedParses++
        continue
      }
      val consumedProjectFileChangeEvidence = consumeProjectFileChangeEvidence(parsedThread)
      if (consumedProjectFileChangeEvidence != null) {
        mayHaveChangedProjectFiles = true
        val evidenceChangedProjectFilePaths = consumedProjectFileChangeEvidence.changedProjectFilePaths
        if (evidenceChangedProjectFilePaths == null) {
          changedProjectFilePaths = null
        }
        else {
          changedProjectFilePaths?.addAll(evidenceChangedProjectFilePaths)
        }
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
      return rolloutSessionUpdate(
        mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
        changedProjectFilePaths = changedProjectFilePathsForUpdate(mayHaveChangedProjectFiles, changedProjectFilePaths),
      )
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
      mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
      changedProjectFilePaths = changedProjectFilePathsForUpdate(mayHaveChangedProjectFiles, changedProjectFilePaths),
    )
  }

  private fun consumeProjectFileChangeEvidence(parsedThread: ParsedRolloutThread): ConsumedProjectFileChangeEvidence? {
    val pathKey = toFileBackedSessionPathKey(parsedThread.path)
    return synchronized(projectFilesChangedAtLock) {
      val previousProjectFilesChangedAt = projectFilesChangedAtByPathKey[pathKey] ?: Long.MIN_VALUE
      val newEvidence = parsedThread.projectFileChangeEvidence.filter { evidence ->
        evidence.timestampMillis > previousProjectFilesChangedAt
      }
      if (newEvidence.isEmpty()) {
        null
      }
      else {
        val projectFilesChangedAt = newEvidence.maxOf { it.timestampMillis }
        projectFilesChangedAtByPathKey[pathKey] = projectFilesChangedAt
        val changedProjectFilePaths = collectChangedProjectFilePaths(newEvidence)
        ConsumedProjectFileChangeEvidence(changedProjectFilePaths = changedProjectFilePaths)
      }
    }
  }

  private fun rememberCachedProjectFileChangeEvidence() {
    val cachedFiles = threadIndex.snapshotCachedFiles()
    synchronized(projectFilesChangedAtLock) {
      // Prune entries whose sessions are no longer tracked so the cache cannot grow unbounded
      // across long IDE sessions that churn many ephemeral rollout files.
      if (projectFilesChangedAtByPathKey.isNotEmpty()) {
        projectFilesChangedAtByPathKey.keys.retainAll(cachedFiles.keys)
      }
      for ((pathKey, cachedFile) in cachedFiles) {
        val projectFilesChangedAt = cachedFile.parsedValue?.projectFilesChangedAt ?: continue
        if (projectFilesChangedAt == Long.MIN_VALUE) {
          continue
        }
        val previousProjectFilesChangedAt = projectFilesChangedAtByPathKey[pathKey] ?: Long.MIN_VALUE
        if (projectFilesChangedAt > previousProjectFilesChangedAt) {
          projectFilesChangedAtByPathKey[pathKey] = projectFilesChangedAt
        }
      }
    }
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
      val threadsByCwd = threadIndex.collectByCwd(setOf(cwdFilter))
      rememberCachedProjectFileChangeEvidence()
      threadsByCwd[cwdFilter].orEmpty()
    }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    return withContext(Dispatchers.IO) {
      val pathFilters = resolvePathFilters(paths)
      if (pathFilters.isEmpty()) return@withContext emptyMap()

      val threadsByCwd = threadIndex.collectByCwd(pathFilters.mapTo(HashSet(pathFilters.size)) { (_, cwdFilter) -> cwdFilter })
      rememberCachedProjectFileChangeEvidence()
      pathFilters.associate { (path, cwdFilter) ->
        path to threadsByCwd.get(cwdFilter).orEmpty()
      }
    }
  }

  override suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): CodexBackendThreadRefreshResult? {
    if (threadIds.isEmpty()) {
      return null
    }
    return withContext(Dispatchers.IO) {
      val workingDirectory = resolveProjectDirectoryFromPath(path)
                             ?: return@withContext CodexBackendThreadRefreshResult()
      val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
      CodexBackendThreadRefreshResult(
        threads = threadIndex.collectByCwdAndThreadIds(cwdFilter = cwdFilter, threadIds = threadIds),
        isComplete = false,
      )
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

private fun rolloutSessionUpdate(
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.HINTS_CHANGED,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

private fun changedProjectFilePathsForUpdate(
  mayHaveChangedProjectFiles: Boolean,
  changedProjectFilePaths: LinkedHashSet<String>?,
): Set<String>? {
  if (!mayHaveChangedProjectFiles) {
    return null
  }
  return changedProjectFilePaths?.takeIf { it.isNotEmpty() }
}

private fun collectChangedProjectFilePaths(evidence: List<CodexProjectFileChangeEvidence>): Set<String>? {
  val changedProjectFilePaths = LinkedHashSet<String>()
  for ((_, itemChangedProjectFilePaths) in evidence) {
    itemChangedProjectFilePaths ?: return null
    changedProjectFilePaths.addAll(itemChangedProjectFilePaths)
  }
  return changedProjectFilePaths.takeIf { it.isNotEmpty() }
}

private data class ConsumedProjectFileChangeEvidence(
  @JvmField val changedProjectFilePaths: Set<String>?,
)

private fun isRolloutPath(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return isRolloutFileName(fileName)
}
