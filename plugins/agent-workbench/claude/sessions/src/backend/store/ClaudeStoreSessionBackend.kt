// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThread
import com.intellij.agent.workbench.claude.sessions.ClaudeBackendThreadRefreshResult
import com.intellij.agent.workbench.claude.sessions.ClaudeSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.createFileBackedSessionChangeFlow
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionActivityHintPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val LOG = logger<ClaudeStoreSessionBackend>()

internal class ClaudeStoreSessionBackend(
  private val claudeHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".claude") },
  changeSource: (() -> Flow<FileBackedSessionChangeSet>)? = null,
) : ClaudeSessionBackend {
  private val store = ClaudeSessionsStore(claudeHomeProvider)
  private val threadIndex = ClaudeThreadIndex(store = store)

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

  private fun buildSessionUpdate(changeSet: FileBackedSessionChangeSet): AgentSessionSourceUpdateEvent {
    if (changeSet.requiresFullRescan || changeSet.changedPaths.isEmpty()) {
      LOG.debug {
        "Claude sessions update is unscoped (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedPaths.size})"
      }
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
    var failedParses = 0
    for (path in jsonlPaths) {
      val parsedThread = store.parseJsonlFile(path)
      val projectPath = parsedThread?.projectPath?.takeIf { it.isNotBlank() }
      if (parsedThread == null || projectPath == null) {
        failedParses++
        continue
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
      return UNSCOPED_CLAUDE_SESSION_UPDATE
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
    )
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
      threadIndex.collectByProject(path)
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
      ClaudeBackendThreadRefreshResult(
        threads = threadIndex.collectByProjectAndSessionIds(projectPath = path, sessionIds = threadIds),
        isComplete = false,
      )
    }
  }
}

private val UNSCOPED_CLAUDE_SESSION_UPDATE = AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED)

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
