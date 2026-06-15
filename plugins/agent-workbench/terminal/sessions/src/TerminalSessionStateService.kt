// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.terminal.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-state-storage.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalRestoreContext
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

private const val TERMINAL_SESSIONS_STATE_VERSION = 2

@Service(Service.Level.APP)
@State(name = "AgentWorkbenchTerminalSessions", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class TerminalSessionStateService
  : SerializablePersistentStateComponent<TerminalSessionsState>(TerminalSessionsState()) {

  private val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)

  val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = updates

  fun recordSession(path: String, threadId: String, title: String, createdAtMs: Long): Boolean {
    val normalizedPath = normalizeTerminalProjectPath(path) ?: return false
    val normalizedThreadId = normalizeTerminalThreadId(threadId) ?: return false
    val normalizedTitle = normalizeTerminalSessionTitle(title)
    var changed = false
    updateState { current ->
      val sessions = current.sessionsForWrite()
      val existing = sessions.firstOrNull { session -> session.matches(normalizedPath, normalizedThreadId) }
      val updatedSession = existing?.copy(
        title = normalizedTitle,
        updatedAt = maxOf(existing.updatedAt, createdAtMs),
        archived = false,
      ) ?: PersistedTerminalSessionState(
        id = normalizedThreadId,
        projectPath = normalizedPath,
        title = normalizedTitle,
        createdAt = createdAtMs,
        updatedAt = createdAtMs,
        archived = false,
      )
      val updatedSessions = sessions.replaceSession(updatedSession)
      changed = updatedSessions != sessions || current.version != TERMINAL_SESSIONS_STATE_VERSION
      if (changed) current.copy(version = TERMINAL_SESSIONS_STATE_VERSION, sessions = updatedSessions) else current
    }
    if (changed) {
      emitThreadsChanged(normalizedPath, normalizedThreadId)
    }
    return true
  }

  fun renameSession(path: String, threadId: String, title: String): Boolean {
    val normalizedTitle = normalizeTerminalSessionTitleOrNull(title) ?: return false
    return updateSession(path = path, threadId = threadId) { session, now ->
      session.copy(title = normalizedTitle, updatedAt = maxOf(session.updatedAt, now))
    }
  }

  fun archiveSession(path: String, threadId: String): Boolean {
    return setArchived(path = path, threadId = threadId, archived = true)
  }

  fun unarchiveSession(path: String, threadId: String): Boolean {
    return setArchived(path = path, threadId = threadId, archived = false)
  }

  fun listSessions(path: String, archived: Boolean): List<AgentSessionThread> {
    val normalizedPath = normalizeTerminalProjectPath(path) ?: return emptyList()
    return state.sessions
      .asSequence()
      .filter { session -> session.projectPath == normalizedPath && session.archived == archived }
      .sortedWith(compareByDescending<PersistedTerminalSessionState> { session -> session.updatedAt }.thenBy { session -> session.title })
      .map(PersistedTerminalSessionState::toAgentSessionThread)
      .toList()
  }

  fun readRestoreContext(path: String, threadId: String): AgentSessionTerminalRestoreContext? {
    val normalizedPath = normalizeTerminalProjectPath(path) ?: return null
    val normalizedThreadId = normalizeTerminalThreadId(threadId) ?: return null
    val session = state.sessions.firstOrNull { session -> session.matches(normalizedPath, normalizedThreadId) } ?: return null
    return session.toRestoreContext()
  }

  fun recordWorkingDirectory(path: String, threadId: String, workingDirectory: String): Boolean {
    val normalizedWorkingDirectory = normalizeTerminalWorkingDirectory(workingDirectory) ?: return false
    return updateSession(path = path, threadId = threadId, emitUpdate = false) { session, _ ->
      if (session.workingDirectory == normalizedWorkingDirectory) session else session.copy(workingDirectory = normalizedWorkingDirectory)
    }
  }

  private fun setArchived(path: String, threadId: String, archived: Boolean): Boolean {
    return updateSession(path = path, threadId = threadId) { session, now ->
      session.copy(archived = archived, updatedAt = maxOf(session.updatedAt, now))
    }
  }

  private fun updateSession(
    path: String,
    threadId: String,
    emitUpdate: Boolean = true,
    update: (PersistedTerminalSessionState, Long) -> PersistedTerminalSessionState,
  ): Boolean {
    val normalizedPath = normalizeTerminalProjectPath(path) ?: return false
    val normalizedThreadId = normalizeTerminalThreadId(threadId) ?: return false
    var found = false
    var changed = false
    updateState { current ->
      val sessions = current.sessionsForWrite()
      val now = System.currentTimeMillis()
      val updatedSessions = sessions.map { session ->
        if (!session.matches(normalizedPath, normalizedThreadId)) {
          session
        }
        else {
          found = true
          update(session, now)
        }
      }
      changed = updatedSessions != sessions || current.version != TERMINAL_SESSIONS_STATE_VERSION
      if (changed) current.copy(version = TERMINAL_SESSIONS_STATE_VERSION, sessions = updatedSessions) else current
    }
    if (emitUpdate && found && changed) {
      emitThreadsChanged(normalizedPath, normalizedThreadId)
    }
    return found
  }

  private fun emitThreadsChanged(path: String, threadId: String) {
    updates.tryEmit(
      AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.THREADS_CHANGED,
        scopedPaths = setOf(path),
        threadIds = setOf(threadId),
      )
    )
  }
}

@Serializable
internal data class TerminalSessionsState(
  @JvmField val version: Int = TERMINAL_SESSIONS_STATE_VERSION,
  @JvmField val sessions: List<PersistedTerminalSessionState> = emptyList(),
)

@Serializable
internal data class PersistedTerminalSessionState(
  @JvmField val id: String,
  @JvmField val projectPath: String,
  @JvmField val title: String,
  @JvmField val createdAt: Long,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean = false,
  @JvmField val workingDirectory: String? = null,
)

private fun TerminalSessionsState.sessionsForWrite(): List<PersistedTerminalSessionState> {
  return if (version == TERMINAL_SESSIONS_STATE_VERSION) sessions else emptyList()
}

private fun List<PersistedTerminalSessionState>.replaceSession(
  updatedSession: PersistedTerminalSessionState,
): List<PersistedTerminalSessionState> {
  var replaced = false
  val updatedSessions = map { session ->
    if (session.matches(updatedSession.projectPath, updatedSession.id)) {
      replaced = true
      updatedSession
    }
    else {
      session
    }
  }
  return if (replaced) updatedSessions else updatedSessions + updatedSession
}

private fun PersistedTerminalSessionState.matches(projectPath: String, threadId: String): Boolean {
  return this.projectPath == projectPath && id == threadId
}

private fun PersistedTerminalSessionState.toAgentSessionThread(): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activity = AgentThreadActivity.READY,
    provider = AgentSessionProvider.TERMINAL,
  )
}

private fun PersistedTerminalSessionState.toRestoreContext(): AgentSessionTerminalRestoreContext {
  return AgentSessionTerminalRestoreContext(
    workingDirectory = workingDirectory,
  )
}

private fun normalizeTerminalProjectPath(path: String): String? {
  return normalizeTerminalStoragePath(path)
}

private fun normalizeTerminalWorkingDirectory(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  return normalizeTerminalStoragePath(trimmedPath)
}

private fun normalizeTerminalStoragePath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPath(trimmedPath)
  return normalizeWindowsDriveRoot(trimmedPath)
         ?: normalizeWindowsDriveRoot(normalizedPath)
         ?: normalizedPath.trimEnd('/').ifEmpty { "/" }
}

private fun normalizeWindowsDriveRoot(path: String): String? {
  if (!WINDOWS_DRIVE_ROOT.matches(path)) return null
  return "${path[0]}:/"
}

private fun normalizeTerminalThreadId(threadId: String): String? {
  return threadId.trim().takeIf { it.isNotEmpty() }
}

private fun normalizeTerminalSessionTitle(title: String): String {
  return normalizeTerminalSessionTitleOrNull(title) ?: DEFAULT_TERMINAL_SESSION_TITLE
}

private fun normalizeTerminalSessionTitleOrNull(title: String): String? {
  return title
    .replace('\n', ' ')
    .replace('\r', ' ')
    .replace(TERMINAL_SESSION_TITLE_WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
}

private val TERMINAL_SESSION_TITLE_WHITESPACE = Regex("\\s+")
private val WINDOWS_DRIVE_ROOT = Regex("[A-Za-z]:[/\\\\]+")

private const val DEFAULT_TERMINAL_SESSION_TITLE = "Terminal"
