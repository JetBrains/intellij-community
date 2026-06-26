// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions.backend.store

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-refresh.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-claude-hooks.spec.md

import com.intellij.platform.ai.agent.claude.common.ClaudeSessionActivity
import com.intellij.platform.ai.agent.claude.common.ClaudeProjectFileChangeEvidence
import com.intellij.platform.ai.agent.claude.common.ClaudeSessionOutline
import com.intellij.platform.ai.agent.claude.common.ClaudeSessionOutlineItem
import com.intellij.platform.ai.agent.claude.common.ClaudeSessionOutlineItemKind
import com.intellij.platform.ai.agent.claude.common.ClaudeSessionThread
import com.intellij.platform.ai.agent.claude.common.ClaudeSessionsStore
import com.intellij.platform.ai.agent.claude.sessions.ClaudeBackendThread
import com.intellij.platform.ai.agent.claude.sessions.ClaudeBackendThreadRefreshResult
import com.intellij.platform.ai.agent.claude.sessions.ClaudeHookBridge
import com.intellij.platform.ai.agent.claude.sessions.CLAUDE_AGENT_SESSION_PROVIDER
import com.intellij.platform.ai.agent.claude.sessions.ClaudeSessionBackend
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionChangeSet
import com.intellij.platform.ai.agent.json.filebacked.createFileBackedSessionChangeFlow
import com.intellij.platform.ai.agent.json.filebacked.toFileBackedSessionPathKey
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

  override val sessionUpdates: Flow<AgentSessionSourceUpdateEvent> = merge(sessionUpdateFlow, ClaudeHookBridge.updateEvents).conflate()

  override val updates: Flow<Unit> = sessionUpdates.map {}

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
    val activityUpdatesByThreadId = LinkedHashMap<String, AgentSessionThreadActivityUpdate>()
    var mayHaveChangedProjectFiles = false
    var changedProjectFilePaths: LinkedHashSet<String>? = LinkedHashSet()
    var failedParses = 0
    for (path in jsonlPaths) {
      val parsedThread = store.parseJsonlFile(path)
      val projectPath = parsedThread?.projectPath?.takeIf { it.isNotBlank() }
      if (parsedThread == null || projectPath == null) {
        failedParses++
        continue
      }
      val consumedProjectFileChangeEvidence = consumeProjectFileChangeEvidence(path = path, parsedThread = parsedThread)
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
      scopedPaths += projectPath
      threadIds += parsedThread.id
      activityUpdatesByThreadId[parsedThread.id] = AgentSessionThreadActivityUpdate(
        activityReport = AgentThreadActivityReport(parsedThread.toAgentThreadActivityHint()),
      )
    }

    if (failedParses > 0 || scopedPaths.isEmpty()) {
      LOG.debug {
        "Claude sessions update falls back to unscoped refresh " +
        "(changedJsonlPaths=${jsonlPaths.size}, failedParses=$failedParses, scopedPaths=${scopedPaths.size})"
      }
      return claudeSessionUpdate(
        mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
        changedProjectFilePaths = changedProjectFilePathsForUpdate(mayHaveChangedProjectFiles, changedProjectFilePaths),
      )
    }

    LOG.debug {
      "Claude sessions update scoped (changedJsonlPaths=${jsonlPaths.size}, scopedPaths=${scopedPaths.size}, threadIds=${threadIds.size})"
    }
    return AgentSessionSourceUpdateEvent(
      type = AgentSessionSourceUpdate.THREADS_CHANGED,
      scopedPaths = scopedPaths,
      threadIds = threadIds.takeIf { it.isNotEmpty() },
      activityUpdatesByThreadId = activityUpdatesByThreadId,
      mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
      changedProjectFilePaths = changedProjectFilePathsForUpdate(mayHaveChangedProjectFiles, changedProjectFilePaths),
    )
  }

  private fun consumeProjectFileChangeEvidence(path: Path, parsedThread: ClaudeSessionThread): ConsumedProjectFileChangeEvidence? {
    val pathKey = toFileBackedSessionPathKey(path)
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
        val changedProjectFilePaths = collectChangedProjectFilePaths(
          evidence = newEvidence,
          projectPath = parsedThread.projectPath,
        )
        ConsumedProjectFileChangeEvidence(changedProjectFilePaths = changedProjectFilePaths)
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

  override suspend fun loadThreadOutline(path: String, threadId: String): AgentSessionThreadOutline? {
    return withContext(Dispatchers.IO) {
      resolveActiveThreadFilePaths(path = path, threadId = threadId)
        .asSequence()
        .mapNotNull(store::parseOutlineJsonlFile)
        .firstOrNull { outline -> outline.sessionId == threadId }
        ?.toAgentSessionThreadOutline()
    }
  }
}

private val UNSCOPED_CLAUDE_SESSION_UPDATE = claudeSessionUpdate()

private fun claudeSessionUpdate(
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.THREADS_CHANGED,
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

private fun collectChangedProjectFilePaths(evidence: List<ClaudeProjectFileChangeEvidence>, projectPath: String?): Set<String>? {
  val changedProjectFilePaths = LinkedHashSet<String>()
  for ((_, itemChangedProjectFilePaths) in evidence) {
    itemChangedProjectFilePaths ?: return null
    for (path in itemChangedProjectFilePaths) {
      val resolvedPath = resolveChangedProjectFilePath(path = path, projectPath = projectPath) ?: return null
      changedProjectFilePaths.add(resolvedPath)
    }
  }
  return changedProjectFilePaths.takeIf { it.isNotEmpty() }
}

private fun resolveChangedProjectFilePath(path: String, projectPath: String?): String? {
  val parsedPath = parseAgentWorkbenchPathOrNull(path)?.normalize() ?: return null
  val resolvedPath = if (parsedPath.isAbsolute) {
    parsedPath
  }
  else {
    val parsedProjectPath = projectPath?.let(::parseAgentWorkbenchPathOrNull)?.takeIf { it.isAbsolute } ?: return null
    parsedProjectPath.resolve(parsedPath).normalize()
  }
  return normalizeAgentWorkbenchPath(resolvedPath.toString())
}

private data class ConsumedProjectFileChangeEvidence(
  @JvmField val changedProjectFilePaths: Set<String>?,
)

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

private fun ClaudeSessionOutline.toAgentSessionThreadOutline(): AgentSessionThreadOutline {
  return AgentSessionThreadOutline(
    provider = CLAUDE_AGENT_SESSION_PROVIDER,
    threadId = sessionId,
    title = title,
    updatedAt = updatedAt,
    items = items.map(ClaudeSessionOutlineItem::toAgentSessionOutlineItem),
  )
}

private fun ClaudeSessionOutlineItem.toAgentSessionOutlineItem(): AgentSessionOutlineItem {
  return AgentSessionOutlineItem(
    id = id,
    kind = kind.toAgentSessionOutlineItemKind(),
    title = title,
    preview = preview,
    timestampMs = timestampMillis,
    children = children.map(ClaudeSessionOutlineItem::toAgentSessionOutlineItem),
  )
}

private fun ClaudeSessionOutlineItemKind.toAgentSessionOutlineItemKind(): AgentSessionOutlineItemKind {
  return this
}
