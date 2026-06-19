// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions.server

import com.intellij.agent.workbench.opencode.sessions.OpenCodeSessionBackend
import com.intellij.agent.workbench.opencode.sessions.OpenCodeSessionIndexEntry
import com.intellij.agent.workbench.opencode.sessions.normalizeOpenCodeProjectPath
import com.intellij.agent.workbench.opencode.sessions.normalizeOpenCodeSessionTitle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

private val LOG = logger<OpenCodeServerSessionBackend>()

@Service(Service.Level.APP)
internal class SharedOpenCodeServerService(serviceScope: CoroutineScope) : OpenCodeSessionBackend {
  private val client = OpenCodeServerClient(coroutineScope = serviceScope)
  private val backend = OpenCodeServerSessionBackend(client)

  init {
    registerOpenCodeShutdownOnCancellation(serviceScope) { client.shutdown() }
  }

  override suspend fun loadEntries(normalizedProjectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry> {
    return backend.loadEntries(normalizedProjectPath, archived)
  }

  override suspend fun renameSession(normalizedProjectPath: String, sessionId: String, title: String): Boolean {
    return backend.renameSession(normalizedProjectPath, sessionId, title)
  }

  override suspend fun archiveSession(normalizedProjectPath: String, sessionId: String, nowMs: Long): Boolean {
    return backend.archiveSession(normalizedProjectPath, sessionId, nowMs)
  }

  override suspend fun unarchiveSession(normalizedProjectPath: String, sessionId: String): Boolean {
    return backend.unarchiveSession(normalizedProjectPath, sessionId)
  }

  companion object {
    fun getInstance(): SharedOpenCodeServerService = service()
  }
}

internal class OpenCodeServerSessionBackend(
  private val client: OpenCodeServerTransport,
) : OpenCodeSessionBackend {
  override suspend fun loadEntries(normalizedProjectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry> {
    return listSessionsForProject(normalizedProjectPath)
      .asSequence()
      .filter { session -> (session.archivedAt != null) == archived }
      .mapNotNull(::toIndexEntry)
      .sortedByDescending(OpenCodeSessionIndexEntry::updatedAt)
      .toList()
  }

  override suspend fun renameSession(normalizedProjectPath: String, sessionId: String, title: String): Boolean {
    return containsVisibleSession(normalizedProjectPath, sessionId) && client.patchSessionTitle(sessionId, title)
  }

  override suspend fun archiveSession(normalizedProjectPath: String, sessionId: String, nowMs: Long): Boolean {
    return containsVisibleSession(normalizedProjectPath, sessionId) && client.patchSessionArchived(sessionId, nowMs)
  }

  override suspend fun unarchiveSession(normalizedProjectPath: String, sessionId: String): Boolean {
    return containsVisibleSession(normalizedProjectPath, sessionId) && client.patchSessionArchived(sessionId, null)
  }

  private suspend fun containsVisibleSession(normalizedProjectPath: String, sessionId: String): Boolean {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return false
    return listSessionsForProject(normalizedProjectPath).any { session -> session.id == normalizedSessionId }
  }

  private suspend fun listSessionsForProject(normalizedProjectPath: String): List<OpenCodeServerSession> {
    val sessionsById = LinkedHashMap<String, OpenCodeServerSession>()
    addMatchingSessions(
      sessionsById = sessionsById,
      sessions = client.listSessions(directory = normalizedProjectPath),
      includeSession = { session -> session.directory.normalizedOpenCodePath() == normalizedProjectPath },
    )

    val worktreeDirectories = resolveWorktreeDirectories(normalizedProjectPath)
    for (directory in worktreeDirectories) {
      addMatchingSessions(
        sessionsById = sessionsById,
        sessions = client.listSessions(directory = directory),
        includeSession = { session -> session.directory.normalizedOpenCodePath() in worktreeDirectories },
      )
    }
    return sessionsById.values.sortedByDescending(OpenCodeServerSession::updatedAt)
  }

  private suspend fun resolveWorktreeDirectories(normalizedProjectPath: String): Set<String> {
    return try {
      val matchingProjects = client.listProjects()
        .filter { project -> project.worktree.normalizedOpenCodePath() == normalizedProjectPath }
      if (matchingProjects.isEmpty()) return emptySet()

      val directories = LinkedHashSet<String>()
      directories.add(normalizedProjectPath)
      for ((projectId) in matchingProjects) {
        client.listProjectDirectories(projectId)
          .mapNotNullTo(directories) { directory -> normalizeOpenCodeProjectPath(directory) }
      }
      directories
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.debug(t) { "Failed to resolve OpenCode worktree directories for $normalizedProjectPath" }
      emptySet()
    }
  }
}

private fun addMatchingSessions(
  sessionsById: MutableMap<String, OpenCodeServerSession>,
  sessions: List<OpenCodeServerSession>,
  includeSession: (OpenCodeServerSession) -> Boolean,
) {
  for (session in sessions) {
    val id = session.id.trim().takeIf { it.isNotEmpty() } ?: continue
    if (!includeSession(session)) continue

    val existing = sessionsById[id]
    if (existing == null || session.updatedAt >= existing.updatedAt) {
      sessionsById[id] = session
    }
  }
}

private fun toIndexEntry(session: OpenCodeServerSession): OpenCodeSessionIndexEntry? {
  val sessionId = session.id.trim().takeIf { it.isNotEmpty() } ?: return null
  return OpenCodeSessionIndexEntry(
    sessionId = sessionId,
    title = session.title?.normalizeOpenCodeSessionTitle() ?: sessionId,
    updatedAt = session.updatedAt,
    archived = session.archivedAt != null,
  )
}

private fun String?.normalizedOpenCodePath(): String? {
  return this?.let(::normalizeOpenCodeProjectPath)
}
