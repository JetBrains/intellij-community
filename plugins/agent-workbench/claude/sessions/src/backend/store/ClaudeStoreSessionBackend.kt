// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThreadRefreshResult
import com.intellij.agent.workbench.claude.sessions.ClaudeSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ClaudeStoreSessionBackend>()

internal class ClaudeStoreSessionBackend(
  private val claudeHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".claude") },
  changeSource: (() -> Flow<FileBackedSessionChangeSet>)? = null,
) : ClaudeSessionBackend {
  private val store = ClaudeSessionsStore(claudeHomeProvider)
  private val threadIndex = ClaudeThreadIndex(store = store)
  private val projectFilesChangedAtByPathKey = HashMap<String, Long>()
  private val projectFilesChangedAtLock = Any()

  private val sessionUpdateFlow: Flow<AgentSessionSourceUpdateEvent> = (changeSource?.invoke() ?: createWatcherUpdates())
    .map { changeSet ->
      threadIndex.markDirty(changeSet)
      withContext(Dispatchers.IO) {
        buildSessionUpdate(changeSet)
      }
    }
    .conflate()

  override val sessionUpdates: Flow<AgentSessionSourceUpdateEvent> = sessionUpdateFlow

  override val updates: Flow<Unit> = sessionUpdateFlow.map {}

  override fun activeThreadFileChangeEvents(path: String, threadId: String): Flow<Unit> {
    return flow {
      val files = withContext(Dispatchers.IO) {
        resolveActiveThreadFilePaths(path = path, threadId = threadId)
      }
      emitAll(agentWorkbenchImmediateFileChangeFlow(files).map {})
    }
  }

  internal fun resolveActiveThreadFilePaths(path: String, threadId: String): List<Path> {
    val normalizedThreadId = threadId.trim().takeIf { id -> id.isNotEmpty() && '/' !in id && '\\' !in id } ?: return emptyList()
    val result = ArrayList<Path>()
    val directories = try {
      store.findMatchingDirectories(path)
    }
    catch (_: Throwable) {
      return emptyList()
    }
    for (directory in directories) {
      val candidate = directory.resolve("$normalizedThreadId.jsonl")
      if (Files.isRegularFile(candidate)) {
        result.add(candidate)
      }
    }
    return result
  }

  private fun buildSessionUpdate(changeSet: FileBackedSessionChangeSet): AgentSessionSourceUpdateEvent {
    if (changeSet.requiresFullRescan) {
      LOG.debug {
        "Claude sessions update is unscoped (fullRescan=true, changedPaths=${changeSet.changedPaths.size})"
      }
      return claudeSessionUpdate(mayHaveChangedProjectFiles = true)
    }

    if (changeSet.changedPaths.isEmpty()) {
      LOG.debug { "Claude sessions update is unscoped (refresh ping)" }
      return UNSCOPED_CLAUDE_SESSION_UPDATE
    }

    if (changeSet.changedPaths.any(::isClaudeSessionIndexPath)) {
      LOG.debug { "Claude sessions update is unscoped (sessions index changed)" }
      return UNSCOPED_CLAUDE_SESSION_UPDATE
    }

    val jsonlPaths = changeSet.changedPaths.filter(::isClaudeJsonlPath)
    if (jsonlPaths.isEmpty()) {
      LOG.debug { "Claude sessions update is unscoped (no JSONL paths in changedPaths=${changeSet.changedPaths.size})" }
      return UNSCOPED_CLAUDE_SESSION_UPDATE
    }

    val scopedPaths = LinkedHashSet<String>()
    val threadIds = LinkedHashSet<String>()
    val activityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity>()
    var mayHaveChangedProjectFiles = false
    var failedParses = 0
    for (path in jsonlPaths) {
      val parsedThread = store.parseJsonlFile(path)
      val projectPath = parsedThread?.projectPath?.takeIf { it.isNotBlank() }
      if (parsedThread == null || projectPath == null) {
        failedParses++
        continue
      }
      if (consumeProjectFileChangeEvidence(path = path, parsedThread = parsedThread)) {
        mayHaveChangedProjectFiles = true
      }
      scopedPaths += projectPath
      threadIds += parsedThread.id
      activityHintsByThreadId[parsedThread.id] = parsedThread.toAgentThreadActivityHint()
    }

    if (failedParses > 0 || scopedPaths.isEmpty()) {
      LOG.debug {
        "Claude sessions update falls back to unscoped refresh " +
        "(changedJsonlPaths=${jsonlPaths.size}, failedParses=$failedParses, scopedPaths=${scopedPaths.size})"
      }
      return claudeSessionUpdate(mayHaveChangedProjectFiles = mayHaveChangedProjectFiles)
    }

    LOG.debug {
      "Claude sessions update scoped (changedJsonlPaths=${jsonlPaths.size}, scopedPaths=${scopedPaths.size}, threadIds=${threadIds.size})"
    }
    return AgentSessionSourceUpdateEvent(
      type = AgentSessionSourceUpdate.THREADS_CHANGED,
      scopedPaths = scopedPaths,
      threadIds = threadIds.takeIf { it.isNotEmpty() },
      activityHintsByThreadId = activityHintsByThreadId,
      activityHintPolicy = AgentSessionActivityHintPolicy.AUTHORITATIVE,
      mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    )
  }

  private fun consumeProjectFileChangeEvidence(path: Path, parsedThread: ClaudeSessionThread): Boolean {
    val projectFilesChangedAt = parsedThread.projectFilesChangedAt
    if (projectFilesChangedAt == Long.MIN_VALUE) {
      return false
    }
    val pathKey = toFileBackedSessionPathKey(path)
    return synchronized(projectFilesChangedAtLock) {
      val previousProjectFilesChangedAt = projectFilesChangedAtByPathKey[pathKey] ?: Long.MIN_VALUE
      if (projectFilesChangedAt <= previousProjectFilesChangedAt) {
        false
      }
      else {
        projectFilesChangedAtByPathKey[pathKey] = projectFilesChangedAt
        true
      }
    }
  }

  private fun rememberCachedProjectFileChangeEvidence() {
    val cachedFiles = threadIndex.snapshotCachedFiles()
    synchronized(projectFilesChangedAtLock) {
      // Prune entries whose sessions are no longer tracked so the cache cannot grow unbounded
      // across long IDE sessions that churn many ephemeral session files.
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
      watcherName = "Claude sessions",
      initContext = { "claudeHome=${claudeHomeProvider()}" },
      emitInitialRefreshPing = true,
    ) { scope, onChange ->
      ClaudeSessionsWatcher(
        claudeHomeProvider = claudeHomeProvider,
        scope = scope,
        onChange = onChange,
      )
    }
  }

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<ClaudeBackendThread> {
    return withContext(Dispatchers.IO) {
      val threads = threadIndex.collectByProject(path)
      rememberCachedProjectFileChangeEvidence()
      threads
    }
  }

  override suspend fun refreshThreads(
    path: String,
    threadIds: Set<String>,
    @Suppress("UNUSED_PARAMETER") openProject: Project?,
  ): ClaudeBackendThreadRefreshResult? {
    if (threadIds.isEmpty()) {
      return null
    }
    return withContext(Dispatchers.IO) {
      val threads = threadIndex.collectByProjectAndSessionIds(projectPath = path, sessionIds = threadIds)
      rememberCachedProjectFileChangeEvidence()
      ClaudeBackendThreadRefreshResult(
        threads = threads,
        isComplete = false,
      )
    }
  }
}

private val UNSCOPED_CLAUDE_SESSION_UPDATE = claudeSessionUpdate()

private fun claudeSessionUpdate(mayHaveChangedProjectFiles: Boolean = false): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.THREADS_CHANGED,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
  )
}

private fun isClaudeJsonlPath(path: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}

private fun isClaudeSessionIndexPath(path: Path): Boolean {
  return path.fileName?.toString() == "sessions-index.json"
}

private fun ClaudeSessionThread.toAgentThreadActivityHint(): AgentThreadActivity {
  return when (activity) {
    ClaudeSessionActivity.PROCESSING -> AgentThreadActivity.PROCESSING
    ClaudeSessionActivity.NEEDS_INPUT -> AgentThreadActivity.NEEDS_INPUT
    ClaudeSessionActivity.READY -> if (awaitingAssistantTurn) AgentThreadActivity.READY else AgentThreadActivity.UNREAD
  }
}
