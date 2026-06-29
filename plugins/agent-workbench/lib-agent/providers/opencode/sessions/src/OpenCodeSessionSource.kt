// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.normalizeAgentSessionProjectPath
import com.intellij.platform.ai.agent.core.normalizeAgentSessionTitle
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.opencode.sessions.server.SharedOpenCodeServerService
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.BaseAgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException

private val LOG = logger<OpenCodeSessionSource>()

internal interface OpenCodeSessionBackend {
  suspend fun loadEntries(normalizedProjectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry>
  suspend fun renameSession(normalizedProjectPath: String, sessionId: String, title: String): Boolean
  suspend fun archiveSession(normalizedProjectPath: String, sessionId: String, nowMs: Long): Boolean
  suspend fun unarchiveSession(normalizedProjectPath: String, sessionId: String): Boolean
}

internal class OpenCodeSessionStore(
  private val backendProvider: () -> OpenCodeSessionBackend = { SharedOpenCodeServerService.getInstance() },
  private val timeProvider: () -> Long = System::currentTimeMillis,
) {
  constructor(
    backend: OpenCodeSessionBackend,
    timeProvider: () -> Long = System::currentTimeMillis,
  ) : this(backendProvider = { backend }, timeProvider = timeProvider)

  suspend fun loadEntries(projectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry> {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(projectPath) ?: return emptyList()
    return try {
      backendProvider().loadEntries(normalizedProjectPath, archived)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Failed to load OpenCode sessions for $normalizedProjectPath", e)
      emptyList()
    }
  }

  suspend fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    val title = normalizedName.normalizeOpenCodeSessionTitle() ?: return false
    return try {
      backendProvider().renameSession(normalizedProjectPath, normalizedThreadId, title)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Failed to rename OpenCode session $normalizedThreadId for $normalizedProjectPath", e)
      false
    }
  }

  suspend fun archiveThread(path: String, threadId: String): Boolean {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    return try {
      backendProvider().archiveSession(normalizedProjectPath, normalizedThreadId, timeProvider())
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Failed to archive OpenCode session $normalizedThreadId for $normalizedProjectPath", e)
      false
    }
  }

  suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    return try {
      backendProvider().unarchiveSession(normalizedProjectPath, normalizedThreadId)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Failed to unarchive OpenCode session $normalizedThreadId for $normalizedProjectPath", e)
      false
    }
  }
}

internal class OpenCodeSessionSource(
  private val sessionStore: OpenCodeSessionStore = OpenCodeSessionStore(),
) : BaseAgentSessionSource(OPENCODE_AGENT_SESSION_PROVIDER), AgentSessionArchivedSource {
  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val entries = sessionStore.loadEntries(projectPath = path, archived = false)
    rememberActiveThreadRead(entries, OpenCodeSessionIndexEntry::sessionId, OpenCodeSessionIndexEntry::updatedAt)
    return entries.map { entry -> entry.toAgentSessionThread(readTracker) }
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return sessionStore.loadEntries(projectPath = path, archived = true)
      .map { entry -> entry.toAgentSessionThread(readTracker) }
  }
}

internal data class OpenCodeSessionIndexEntry(
  val sessionId: String,
  val title: String,
  val updatedAt: Long,
  val archived: Boolean,
)

private fun OpenCodeSessionIndexEntry.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activityReport = AgentThreadActivityReport(resolveReadTrackedActivity(readTracker = readTracker, threadId = sessionId, updatedAt = updatedAt)),
    provider = OPENCODE_AGENT_SESSION_PROVIDER,
  )
}

internal fun normalizeOpenCodeProjectPath(path: String): String? {
  return normalizeAgentSessionProjectPath(path)
}

internal fun String.normalizeOpenCodeSessionTitle(): String? {
  return normalizeAgentSessionTitle(this)
}
