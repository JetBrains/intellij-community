// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Timeout(value = 2, unit = TimeUnit.MINUTES)
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

  @Test
  fun `load thread costs reads requested session root events`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-cost")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-cost",
          projectDir = projectDir,
          taskName = "Cost thread",
          updatedAt = 3000L,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-cost",
        sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "0.10"),
        sessionA2uxLlmEvent(model = "gemini-3-flash-preview", cost = "0.25"),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val threads = source.listThreadsFromClosedProject(projectDir.toString())
      val thread = threads.single()
      assertThat(thread.cost).isNull()

      val loadedCosts = source.loadThreadCosts(projectDir.toString(), listOf(thread))

      assertThat(loadedCosts).containsEntry(
        "session-cost",
        AgentSessionCost(
          amountUsd = BigDecimal("0.35"),
          kind = AgentSessionCostKind.EXACT,
        ),
      )
    }
  }

  @Test
  fun `load thread costs works for archived threads`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archived-cost")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-archived-cost",
          projectDir = projectDir,
          taskName = "Archived cost",
          updatedAt = 4000L,
          archived = true,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-archived-cost",
        sessionA2uxLlmEvent(model = "gemini-3-flash-preview", cost = "1.25"),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val archivedThreads = source.listArchivedThreadsFromClosedProject(projectDir.toString())
      val loadedCosts = source.loadThreadCosts(projectDir.toString(), archivedThreads)

      assertThat(archivedThreads.single().cost).isNull()
      assertThat(loadedCosts["session-archived-cost"]?.amountUsd).isEqualByComparingTo("1.25")
      assertThat(loadedCosts["session-archived-cost"]?.kind).isEqualTo(AgentSessionCostKind.EXACT)
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

  private fun writeJunieEvents(sessionsRoot: Path, sessionId: String, vararg lines: String) {
    val sessionDir = sessionsRoot.resolve(sessionId)
    Files.createDirectories(sessionDir)
    Files.writeString(sessionDir.resolve("events.jsonl"), lines.joinToString(separator = "\n", postfix = "\n"))
  }

  private fun sessionA2uxLlmEvent(model: String, cost: String?): String {
    val costField = cost?.let { "\"cost\":$it," } ?: ""
    return """
      {"kind":"SessionA2uxEvent","event":{"agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"$model",${costField}"inputTokens":1,"cacheInputTokens":0,"cacheCreateTokens":0,"outputTokens":1}]}}}
    """.trimIndent()
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
