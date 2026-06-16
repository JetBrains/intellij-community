// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class OpenCodeSessionSourceTest {
  @Test
  fun listsOpenCodeSessionsForProjectFromDatabase(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val otherProjectPath = tempDir.resolve("other").toString()
    val dbPath = tempDir.resolve("opencode.db")
    createOpenCodeDatabase(dbPath) { connection ->
      connection.insertProject(id = "project-1", worktree = projectPath)
      connection.insertProject(id = "project-2", worktree = otherProjectPath)
      connection.insertSession(
        id = "old-session",
        projectId = "project-1",
        directory = projectPath,
        title = "  Older\nthread  ",
        updatedAt = 100L,
      )
      connection.insertSession(
        id = "new-session",
        projectId = "project-1",
        directory = projectPath,
        title = "New thread",
        updatedAt = 300L,
      )
      connection.insertSession(
        id = "worktree-session",
        projectId = "project-1",
        directory = tempDir.resolve("nested").toString(),
        title = "Worktree session",
        updatedAt = 200L,
      )
      connection.insertSession(
        id = "other-session",
        projectId = "project-2",
        directory = otherProjectPath,
        title = "Other thread",
        updatedAt = 400L,
      )
      connection.insertSession(
        id = "archived-session",
        projectId = "project-1",
        directory = projectPath,
        title = "Archived thread",
        updatedAt = 500L,
        archivedAt = 600L,
      )
    }
    val source = OpenCodeSessionSource(OpenCodeSessionStore(dbPathResolver = { dbPath }))

    val threads = source.listThreadsFromClosedProject("$projectPath/")

    assertThat(threads.map { it.id }).containsExactly("new-session", "worktree-session", "old-session")
    assertThat(threads.map { it.title }).containsExactly("New thread", "Worktree session", "Older thread")
    assertThat(threads.map { it.provider }).containsOnly(AgentSessionProvider.OPENCODE)
    assertThat(threads.map { it.archived }).containsOnly(false)
    assertThat(threads.map { it.activity }).containsOnly(AgentThreadActivity.READY)
  }

  @Test
  fun listsArchivedOpenCodeSessionsForProjectFromDatabase(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val dbPath = tempDir.resolve("opencode.db")
    createOpenCodeDatabase(dbPath) { connection ->
      connection.insertProject(id = "project-1", worktree = projectPath)
      connection.insertSession(
        id = "visible-session",
        projectId = "project-1",
        directory = projectPath,
        title = "Visible thread",
        updatedAt = 100L,
      )
      connection.insertSession(
        id = "archived-session",
        projectId = "project-1",
        directory = projectPath,
        title = "Archived thread",
        updatedAt = 300L,
        archivedAt = 400L,
      )
    }
    val source = OpenCodeSessionSource(OpenCodeSessionStore(dbPathResolver = { dbPath }))

    val threads = source.listArchivedThreadsFromClosedProject(projectPath)

    assertThat(threads.map { it.id }).containsExactly("archived-session")
    assertThat(threads.single().archived).isTrue()
    assertThat(threads.single().provider).isEqualTo(AgentSessionProvider.OPENCODE)
  }

  @Test
  fun descriptorRenamesArchivesAndUnarchivesOpenCodeSessions(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val dbPath = tempDir.resolve("opencode.db")
    createOpenCodeDatabase(dbPath) { connection ->
      connection.insertProject(id = "project-1", worktree = projectPath)
      connection.insertSession(
        id = "session-1",
        projectId = "project-1",
        directory = projectPath,
        title = "Original title",
        updatedAt = 100L,
      )
    }
    val store = OpenCodeSessionStore(dbPathResolver = { dbPath }, timeProvider = { 1_000L })
    val descriptor = OpenCodeAgentSessionProviderDescriptor(
      sessionStore = store,
      executableResolver = { "opencode" },
      cliAvailableProbe = { true },
    )

    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.threadRenameAction.invoke(projectPath, "session-1", "  Renamed\nthread  ")).isTrue()

    val renamedThread = descriptor.sessionSource.listThreadsFromClosedProject(projectPath).single()
    assertThat(renamedThread.title).isEqualTo("Renamed thread")
    assertThat(renamedThread.updatedAt).isEqualTo(1_000L)

    assertThat(descriptor.archiveThread(projectPath, "session-1")).isTrue()
    assertThat(descriptor.sessionSource.listThreadsFromClosedProject(projectPath)).isEmpty()
    assertThat(descriptor.sessionSource.listArchivedThreadsFromClosedProject(projectPath).single().id).isEqualTo("session-1")

    assertThat(descriptor.unarchiveThread(projectPath, "session-1")).isTrue()
    assertThat(descriptor.sessionSource.listArchivedThreadsFromClosedProject(projectPath)).isEmpty()
    assertThat(descriptor.sessionSource.listThreadsFromClosedProject(projectPath).single().id).isEqualTo("session-1")
  }

  @Test
  fun returnsEmptyListWhenDatabaseDoesNotExist(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val source = OpenCodeSessionSource(OpenCodeSessionStore(dbPathResolver = { tempDir.resolve("missing.db") }))

    assertThat(source.listThreadsFromClosedProject(tempDir.toString())).isEmpty()
    assertThat(source.listArchivedThreadsFromClosedProject(tempDir.toString())).isEmpty()
  }
}

private fun createOpenCodeDatabase(dbPath: Path, populate: (Connection) -> Unit) {
  Class.forName("org.sqlite.JDBC")
  DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath().normalize()}").use { connection ->
    connection.createStatement().use { statement ->
      statement.executeUpdate("""
        CREATE TABLE "project" (
          id TEXT PRIMARY KEY,
          worktree TEXT NOT NULL,
          name TEXT
        )
      """.trimIndent())
      statement.executeUpdate("""
        CREATE TABLE "session" (
          id TEXT PRIMARY KEY,
          project_id TEXT NOT NULL,
          directory TEXT NOT NULL,
          title TEXT,
          time_created INTEGER NOT NULL,
          time_updated INTEGER NOT NULL,
          time_archived INTEGER
        )
      """.trimIndent())
    }
    populate(connection)
  }
}

private fun Connection.insertProject(id: String, worktree: String) {
  prepareStatement("INSERT INTO \"project\" (id, worktree, name) VALUES (?, ?, ?)").use { statement ->
    statement.setString(1, id)
    statement.setString(2, worktree)
    statement.setString(3, id)
    statement.executeUpdate()
  }
}

private fun Connection.insertSession(
  id: String,
  projectId: String,
  directory: String,
  title: String,
  updatedAt: Long,
  archivedAt: Long? = null,
) {
  prepareStatement("""
    INSERT INTO "session" (id, project_id, directory, title, time_created, time_updated, time_archived)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  """.trimIndent()).use { statement ->
    statement.setString(1, id)
    statement.setString(2, projectId)
    statement.setString(3, directory)
    statement.setString(4, title)
    statement.setLong(5, updatedAt)
    statement.setLong(6, updatedAt)
    if (archivedAt == null) {
      statement.setNull(7, java.sql.Types.INTEGER)
    }
    else {
      statement.setLong(7, archivedAt)
    }
    statement.executeUpdate()
  }
}
