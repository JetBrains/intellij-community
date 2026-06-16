// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet

private val LOG = logger<OpenCodeSessionSource>()

internal class OpenCodeSessionStore(
  private val dbPathResolver: () -> Path = ::resolveDefaultOpenCodeDatabasePath,
  private val timeProvider: () -> Long = System::currentTimeMillis,
) {
  fun loadEntries(projectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry> {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(projectPath) ?: return emptyList()
    val dbPath = dbPathResolver()
    if (!Files.isRegularFile(dbPath)) return emptyList()

    return try {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath().normalize()}").use { connection ->
        connection.prepareStatement(OPEN_CODE_SESSIONS_QUERY).use { statement ->
          statement.setBoolean(1, archived)
          statement.setBoolean(2, archived)
          statement.executeQuery().use { resultSet ->
            val entries = ArrayList<OpenCodeSessionIndexEntry>()
            while (resultSet.next()) {
              resultSet.readOpenCodeSessionEntry(normalizedProjectPath)?.let(entries::add)
            }
            entries.sortedByDescending(OpenCodeSessionIndexEntry::updatedAt)
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to load OpenCode sessions from $dbPath", e)
      emptyList()
    }
  }

  private fun ResultSet.readOpenCodeSessionEntry(normalizedProjectPath: String): OpenCodeSessionIndexEntry? {
    val normalizedDirectory = getString("directory")?.let(::normalizeOpenCodeProjectPath)
    val normalizedWorktree = getString("worktree")?.let(::normalizeOpenCodeProjectPath)
    if (normalizedDirectory != normalizedProjectPath && normalizedWorktree != normalizedProjectPath) return null

    val sessionId = getString("id")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val title = getString("title")?.normalizeOpenCodeSessionTitle() ?: sessionId
    val updatedAt = getLong("time_updated")
    return OpenCodeSessionIndexEntry(
      sessionId = sessionId,
      title = title,
      updatedAt = updatedAt,
      archived = getObject("time_archived") != null,
    )
  }

  fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    val title = normalizedName.normalizeOpenCodeSessionTitle() ?: return false
    return updateSession(
      path = path,
      threadId = normalizedThreadId,
      sql = "UPDATE \"session\" SET title = ?, time_updated = ? WHERE id = ? AND project_id IN (${OPEN_CODE_PROJECT_FILTER_QUERY})",
    ) { statement ->
      statement.setString(1, title)
      statement.setLong(2, timeProvider())
      statement.setString(3, normalizedThreadId)
      4
    }
  }

  fun archiveThread(path: String, threadId: String): Boolean {
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateSession(
      path = path,
      threadId = normalizedThreadId,
      sql = "UPDATE \"session\" SET time_archived = ?, time_updated = ? WHERE id = ? AND project_id IN (${OPEN_CODE_PROJECT_FILTER_QUERY})",
    ) { statement ->
      val now = timeProvider()
      statement.setLong(1, now)
      statement.setLong(2, now)
      statement.setString(3, normalizedThreadId)
      4
    }
  }

  fun unarchiveThread(path: String, threadId: String): Boolean {
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateSession(
      path = path,
      threadId = normalizedThreadId,
      sql = "UPDATE \"session\" SET time_archived = NULL, time_updated = ? WHERE id = ? AND project_id IN (${OPEN_CODE_PROJECT_FILTER_QUERY})",
    ) { statement ->
      statement.setLong(1, timeProvider())
      statement.setString(2, normalizedThreadId)
      3
    }
  }

  private fun updateSession(
    path: String,
    threadId: String,
    sql: String,
    bindSessionFields: (java.sql.PreparedStatement) -> Int,
  ): Boolean {
    val normalizedProjectPath = normalizeOpenCodeProjectPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    val dbPath = dbPathResolver()

    return openCodeDatabaseExists(dbPath) && try {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath().normalize()}").use { connection ->
        connection.prepareStatement(sql).use { statement ->
          val nextParameterIndex = bindSessionFields(statement)
          statement.setString(nextParameterIndex, normalizedProjectPath)
          statement.setString(nextParameterIndex + 1, normalizedProjectPath)
          statement.executeUpdate() > 0
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to update OpenCode session $normalizedThreadId in $dbPath", e)
      false
    }
  }
}

private fun openCodeDatabaseExists(dbPath: Path): Boolean = Files.isRegularFile(dbPath)

internal class OpenCodeSessionSource(
  private val sessionStore: OpenCodeSessionStore = OpenCodeSessionStore(),
) : BaseAgentSessionSource(AgentSessionProvider.OPENCODE) {
  override val supportsArchivedThreads: Boolean
    get() = true

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
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
    activity = resolveReadTrackedActivity(readTracker = readTracker, threadId = sessionId, updatedAt = updatedAt),
    provider = AgentSessionProvider.OPENCODE,
  )
}

internal fun resolveDefaultOpenCodeDatabasePath(
  environmentProvider: (String) -> String? = System::getenv,
  homeDirProvider: () -> String = { System.getProperty("user.home") ?: "." },
): Path {
  val xdgDataHome = environmentProvider("XDG_DATA_HOME")?.trim()?.takeIf { it.isNotEmpty() }
  if (xdgDataHome != null) return Path.of(xdgDataHome, "opencode", "opencode.db")
  return Path.of(homeDirProvider(), ".local", "share", "opencode", "opencode.db")
}

internal fun normalizeOpenCodeProjectPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPathOrNull(trimmedPath) ?: return null
  return normalizedPath.trimEnd('/').ifEmpty { "/" }
}

private fun String.normalizeOpenCodeSessionTitle(): String? {
  return replace('\n', ' ')
    .replace('\r', ' ')
    .replace(OPEN_CODE_THREAD_TITLE_WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
}

private val OPEN_CODE_THREAD_TITLE_WHITESPACE = Regex("\\s+")

private const val OPEN_CODE_SESSIONS_QUERY: String = """
  SELECT
    s.id,
    s.title,
    s.directory,
    s.time_updated,
    s.time_archived,
    p.worktree
  FROM "session" s
  JOIN "project" p ON p.id = s.project_id
  WHERE (? AND s.time_archived IS NOT NULL) OR (NOT ? AND s.time_archived IS NULL)
"""

private const val OPEN_CODE_PROJECT_FILTER_QUERY: String = """
  SELECT id
  FROM "project"
  WHERE worktree = ?
  UNION
  SELECT project_id
  FROM "session"
  WHERE directory = ?
"""
