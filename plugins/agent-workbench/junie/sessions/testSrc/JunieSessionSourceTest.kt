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

      assertThat(threads.map { it.id }).containsExactly("session-1", "session-2")
      assertThat(threads[0].title).isEqualTo("Fix Junie loading")
      assertThat(threads[0].updatedAt).isEqualTo(3000L)
      assertThat(threads[0].archived).isFalse()
      assertThat(threads[0].activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads[0].provider).isEqualTo(AgentSessionProvider.JUNIE)
      assertThat(threads[1].title).isEqualTo("Review changes")
    }
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
}

private fun sessionIndexLine(
  sessionId: String,
  projectDir: Path,
  taskName: String,
  updatedAt: Long,
): String {
  val escapedProjectDir = projectDir.toString().jsonEscape()
  val escapedTaskName = taskName.jsonEscape()
  return listOf(
    "\"sessionId\":\"$sessionId\"",
    "\"createdAt\":1000",
    "\"updatedAt\":$updatedAt",
    "\"projectDir\":\"$escapedProjectDir\"",
    "\"taskName\":\"$escapedTaskName\"",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun String.jsonEscape(): String {
  return replace("\\", "\\\\").replace("\"", "\\\"")
}
