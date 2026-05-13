// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JunieSessionSourceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `loads indexed sessions for matching project path`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-a")
      val otherProjectDir = tempDir.resolve("project-b")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-1",
          projectDir = projectDir,
          taskName = "Fix Junie loading",
          updatedAt = 3000L,
          archived = true,
        ),
        sessionIndexLine(
          sessionId = "session-2",
          projectDir = projectDir,
          taskName = "Review changes",
          updatedAt = 2000L,
        ),
        sessionIndexLine(
          sessionId = "session-other",
          projectDir = otherProjectDir,
          taskName = "Other project",
          updatedAt = 4000L,
        ),
        """{"sessionId":"missing-project","updatedAt":5000,"taskName":"Incomplete"}""",
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val threads = source.listThreadsFromClosedProject("$projectDir/")

      assertThat(threads.map { it.id }).containsExactly("session-2")
      assertThat(threads[0].title).isEqualTo("Review changes")
      assertThat(threads[0].updatedAt).isEqualTo(2000L)
      assertThat(threads[0].archived).isFalse()
      assertThat(threads[0].activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads[0].provider).isEqualTo(AgentSessionProvider.JUNIE)
    }
  }

  @Test
  fun `loads archived indexed sessions for matching project path`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archived-list")
      val otherProjectDir = tempDir.resolve("project-other")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-active",
          projectDir = projectDir,
          taskName = "Active",
          updatedAt = 3000L,
        ),
        sessionIndexLine(
          sessionId = "session-archived",
          projectDir = projectDir,
          taskName = "Archived",
          updatedAt = 4000L,
          archived = true,
        ),
        sessionIndexLine(
          sessionId = "session-other",
          projectDir = otherProjectDir,
          taskName = "Other",
          updatedAt = 5000L,
          archived = true,
        ),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val threads = source.listArchivedThreadsFromClosedProject(projectDir.toString())

      assertThat(threads.map { it.id }).containsExactly("session-archived")
      assertThat(threads.single().title).isEqualTo("Archived")
      assertThat(threads.single().archived).isTrue()
      assertThat(threads.single().activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads.single().provider).isEqualTo(AgentSessionProvider.JUNIE)
    }
  }

  @Test
  fun `rename appends updated indexed entry`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rename")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-rename",
          projectDir = projectDir,
          taskName = "Old title",
          updatedAt = 3000L,
        ),
      )
      val store = JunieSessionIndexStore(sessionIndexPathProvider = { index }, timeProvider = { 4000L })
      val source = JunieSessionSource(sessionIndexStore = store)

      val renamed = store.renameThread(projectDir.toString(), "session-rename", "New title")

      assertThat(renamed).isTrue()
      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(thread.title).isEqualTo("New title")
      assertThat(thread.updatedAt).isEqualTo(4000L)
      assertThat(thread.archived).isFalse()
      assertThat(indexLineCount(index)).isEqualTo(2)
    }
  }

  @Test
  fun `archive and unarchive survive Junie index rewrite`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archive")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-archive",
          projectDir = projectDir,
          taskName = "Archive me",
          updatedAt = 3000L,
        ),
      )
      val archiveState = archiveStatePath(index)
      var now = 4000L
      val store = JunieSessionIndexStore(sessionIndexPathProvider = { index }, timeProvider = { now })
      val source = JunieSessionSource(sessionIndexStore = store)

      val archived = store.archiveThread("$projectDir/", "session-archive")

      assertThat(archived).isTrue()
      assertThat(source.listThreadsFromClosedProject(projectDir.toString())).isEmpty()
      rewriteIndex(
        index,
        sessionIndexLine(
          sessionId = "session-archive",
          projectDir = projectDir,
          taskName = "Junie refreshed archived session",
          updatedAt = 4500L,
        ),
      )
      assertThat(source.listThreadsFromClosedProject(projectDir.toString())).isEmpty()

      now = 5000L
      val unarchived = store.unarchiveThread(projectDir.toString(), "session-archive")
      rewriteIndex(
        index,
        sessionIndexLine(
          sessionId = "session-archive",
          projectDir = projectDir,
          taskName = "Junie refreshed unarchived session",
          updatedAt = 5500L,
        ),
      )

      assertThat(unarchived).isTrue()
      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(thread.title).isEqualTo("Junie refreshed unarchived session")
      assertThat(thread.updatedAt).isEqualTo(5500L)
      assertThat(thread.archived).isFalse()
      assertThat(indexLineCount(archiveState)).isEqualTo(2)
    }
  }

  @Test
  fun `mutation returns false when session is not indexed for project`() {
    val projectDir = tempDir.resolve("project-missing")
    val index = writeIndex(
      sessionIndexLine(
        sessionId = "session-existing",
        projectDir = projectDir,
        taskName = "Existing",
        updatedAt = 3000L,
      ),
    )
    val store = JunieSessionIndexStore(sessionIndexPathProvider = { index }, timeProvider = { 4000L })

    assertThat(store.renameThread(projectDir.toString(), "session-missing", "New title")).isFalse()
    assertThat(store.archiveThread(tempDir.resolve("other-project").toString(), "session-existing")).isFalse()
    assertThat(indexLineCount(index)).isEqualTo(1)
  }

  @Test
  fun `latest indexed entry wins for duplicate session ids`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-duplicates")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-duplicate",
          projectDir = projectDir,
          taskName = "Old title",
          updatedAt = 2000L,
        ),
        sessionIndexLine(
          sessionId = "session-duplicate",
          projectDir = projectDir,
          taskName = "New title",
          updatedAt = 4000L,
        ),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val threads = source.listThreadsFromClosedProject(projectDir.toString())

      val thread = threads.single()
      assertThat(thread.id).isEqualTo("session-duplicate")
      assertThat(thread.title).isEqualTo("New title")
      assertThat(thread.updatedAt).isEqualTo(4000L)
    }
  }

  @Test
  fun `read tracker marks updated indexed session unread`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-read-state")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-read-state",
          projectDir = projectDir,
          taskName = "Read state",
          updatedAt = 3000L,
        ),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      source.markThreadAsRead("session-read-state", 2000L)

      val unreadThreads = source.listThreadsFromClosedProject(projectDir.toString())
      assertThat(unreadThreads.single().activity).isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("session-read-state", 3000L)

      val readyThreads = source.listThreadsFromClosedProject(projectDir.toString())
      assertThat(readyThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  private fun writeIndex(vararg lines: String): Path {
    val index = tempDir.resolve(".junie").resolve("sessions").resolve("index.jsonl")
    Files.createDirectories(index.parent)
    Files.writeString(index, lines.joinToString(separator = "\n", postfix = "\n"))
    return index
  }

  private fun rewriteIndex(index: Path, vararg lines: String) {
    Files.writeString(index, lines.joinToString(separator = "\n", postfix = "\n"))
  }

  private fun archiveStatePath(index: Path): Path {
    return index.parent.resolve("agent-workbench-archive-state.jsonl")
  }

  private fun indexLineCount(index: Path): Int {
    return Files.readString(index).lineSequence().count { it.isNotBlank() }
  }
}

private fun sessionIndexLine(
  sessionId: String,
  projectDir: Path,
  taskName: String,
  updatedAt: Long,
  archived: Boolean? = null,
): String {
  val escapedProjectDir = projectDir.toString().jsonEscape()
  val escapedTaskName = taskName.jsonEscape()
  val fields = mutableListOf(
    "\"sessionId\":\"$sessionId\"",
    "\"createdAt\":1000",
    "\"updatedAt\":$updatedAt",
    "\"projectDir\":\"$escapedProjectDir\"",
    "\"taskName\":\"$escapedTaskName\"",
  )
  if (archived != null) {
    fields += "\"archived\":$archived"
  }
  return fields.joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun String.jsonEscape(): String {
  return replace("\\", "\\\\").replace("\"", "\\\"")
}
