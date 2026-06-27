// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionChangeSet
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
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

      val threads = source.listThreads("$projectDir/", openProject = null)

      assertThat(threads.map { it.id }).containsExactly("session-2")
      assertThat(threads[0].title).isEqualTo("Review changes")
      assertThat(threads[0].updatedAt).isEqualTo(2000L)
      assertThat(threads[0].archived).isFalse()
      assertThat(threads[0].activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads[0].provider).isEqualTo(AgentSessionProvider.from("junie"))
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

      val threads = source.listArchivedThreads(projectDir.toString(), openProject = null)

      assertThat(threads.map { it.id }).containsExactly("session-archived")
      assertThat(threads.single().title).isEqualTo("Archived")
      assertThat(threads.single().archived).isTrue()
      assertThat(threads.single().activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads.single().provider).isEqualTo(AgentSessionProvider.from("junie"))
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
      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
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
      assertThat(source.listThreads(projectDir.toString(), openProject = null)).isEmpty()
      rewriteIndex(
        index,
        sessionIndexLine(
          sessionId = "session-archive",
          projectDir = projectDir,
          taskName = "Junie refreshed archived session",
          updatedAt = 4500L,
        ),
      )
      assertThat(source.listThreads(projectDir.toString(), openProject = null)).isEmpty()

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
      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
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

      val threads = source.listThreads(projectDir.toString(), openProject = null)

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

      val unreadThreads = source.listThreads(projectDir.toString(), openProject = null)
      assertThat(unreadThreads.single().activity).isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("session-read-state", 3000L)

      val readyThreads = source.listThreads(projectDir.toString(), openProject = null)
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

      val threads = source.listThreads(projectDir.toString(), openProject = null)
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

      val archivedThreads = source.listArchivedThreads(projectDir.toString(), openProject = null)
      val loadedCosts = source.loadThreadCosts(projectDir.toString(), archivedThreads)

      assertThat(archivedThreads.single().cost).isNull()
      assertThat(loadedCosts["session-archived-cost"]?.amountUsd).isEqualByComparingTo("1.25")
      assertThat(loadedCosts["session-archived-cost"]?.kind).isEqualTo(AgentSessionCostKind.EXACT)
    }
  }

  @Test
  fun `thread scoped refresh projects running Junie event activity`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-running-status")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-running-status",
          projectDir = projectDir,
          taskName = "Running status",
          updatedAt = 3000L,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-running-status",
        sessionA2uxStatusEvent(status = "Sending LLM request"),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val listedThreads = source.listThreads(projectDir.toString(), openProject = null)
      assertThat(listedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)

      val refreshResult = source.refreshThreads(
        threadScopedRequest(projectDir, "session-running-status")
      )

      val refreshedThread = refreshResult.partialThreadsByPath.getValue(projectDir.toString()).single()
      assertThat(refreshedThread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(source.listThreads(projectDir.toString(), openProject = null).single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `thread scoped refresh falls back to read tracker when Junie status is complete`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-complete-status")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-complete-status",
          projectDir = projectDir,
          taskName = "Complete status",
          updatedAt = 3000L,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-complete-status",
        sessionA2uxStatusEvent(status = "Sending LLM request"),
        sessionA2uxStatusEvent(status = null),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })
      source.markThreadAsRead("session-complete-status", 2000L)

      val refreshResult = source.refreshThreads(
        threadScopedRequest(projectDir, "session-complete-status")
      )

      val refreshedThread = refreshResult.partialThreadsByPath.getValue(projectDir.toString()).single()
      assertThat(refreshedThread.activity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun `prefetch refresh hints returns Junie rebind candidates and activity updates`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-refresh-hints")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-known-hint",
          projectDir = projectDir,
          taskName = "Known hint",
          updatedAt = 3000L,
        ),
        sessionIndexLine(
          sessionId = "session-rebind-hint",
          projectDir = projectDir,
          taskName = "Materialized Junie title",
          updatedAt = 4000L,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-known-hint",
        sessionA2uxTerminalEvent(status = "IN_PROGRESS", stepId = "step-1"),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })

      val hints = source.prefetchRefreshHints(
        paths = listOf(projectDir.toString()),
        refreshThreadSeedsByPath = mapOf(
          projectDir.toString() to setOf(AgentSessionRefreshThreadSeed(threadId = "session-known-hint"))
        ),
      ).getValue(projectDir.toString())

      assertThat(hints.activityUpdatesByThreadId.getValue("session-known-hint").activityReport.rowActivity)
        .isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(hints.presentationUpdatesByThreadId.getValue("session-known-hint").title).isEqualTo("Known hint")
      assertThat(hints.rebindCandidates.map { it.threadId }).containsExactly("session-rebind-hint")
      assertThat(hints.rebindCandidates.single().title).isEqualTo("Materialized Junie title")
    }
  }

  @Test
  fun `load thread costs reuses cached Junie cost until updatedAt changes`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-cost-cache")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-cost-cache",
          projectDir = projectDir,
          taskName = "Cost cache",
          updatedAt = 3000L,
        ),
      )
      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-cost-cache",
        sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "0.10"),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })
      val initialThread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(initialThread))["session-cost-cache"]?.amountUsd)
        .isEqualByComparingTo("0.10")

      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-cost-cache",
        sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "20.00"),
      )
      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(initialThread))["session-cost-cache"]?.amountUsd)
        .isEqualByComparingTo("0.10")

      rewriteIndex(
        index,
        sessionIndexLine(
          sessionId = "session-cost-cache",
          projectDir = projectDir,
          taskName = "Cost cache",
          updatedAt = 4000L,
        ),
      )
      val updatedThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(updatedThread))["session-cost-cache"]?.amountUsd)
        .isEqualByComparingTo("20.00")
    }
  }

  @Test
  fun `load thread costs caches unavailable Junie cost until updatedAt changes`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-missing-cost-cache")
      val index = writeIndex(
        sessionIndexLine(
          sessionId = "session-missing-cost-cache",
          projectDir = projectDir,
          taskName = "Missing cost cache",
          updatedAt = 3000L,
        ),
      )
      val source = JunieSessionSource(sessionIndexPathProvider = { index })
      val initialThread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(initialThread)))
        .containsEntry("session-missing-cost-cache", null)

      writeJunieEvents(
        sessionsRoot = index.parent,
        sessionId = "session-missing-cost-cache",
        sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "0.20"),
      )
      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(initialThread)))
        .containsEntry("session-missing-cost-cache", null)

      rewriteIndex(
        index,
        sessionIndexLine(
          sessionId = "session-missing-cost-cache",
          projectDir = projectDir,
          taskName = "Missing cost cache",
          updatedAt = 4000L,
        ),
      )
      val updatedThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(source.loadThreadCosts(projectDir.toString(), listOf(updatedThread))["session-missing-cost-cache"]?.amountUsd)
        .isEqualByComparingTo("0.20")
    }
  }

  @Test
  fun `Junie backend emits scoped event update for session events file`() {
    val projectDir = tempDir.resolve("project-backend-events")
    val index = writeIndex(
      sessionIndexLine(
        sessionId = "session-backend-events",
        projectDir = projectDir,
        taskName = "Backend events",
        updatedAt = 3000L,
      ),
    )
    writeJunieEvents(
      sessionsRoot = index.parent,
      sessionId = "session-backend-events",
      sessionA2uxStatusEvent(status = "Sending LLM request"),
    )
    val backend = JunieSessionUpdateBackend(
      sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider = { index }),
      eventsAnalyzer = JunieSessionEventsAnalyzer(sessionsRootPathProvider = { index.parent }),
    )

    val update = backend.buildSessionUpdate(
      FileBackedSessionChangeSet(changedPaths = setOf(index.parent.resolve("session-backend-events").resolve("events.jsonl")))
    )

    assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(update.scopedPaths).containsExactly(projectDir.toString())
    assertThat(update.threadIds).containsExactly("session-backend-events")
    assertThat(update.mayHaveChangedProjectFiles).isFalse()
  }

  @Test
  fun `Junie backend emits thread-only hint when event path cannot be mapped to project`() {
    val index = writeIndex()
    writeJunieEvents(
      sessionsRoot = index.parent,
      sessionId = "session-unmapped-events",
      sessionA2uxStatusEvent(status = "Sending LLM request"),
    )
    val backend = JunieSessionUpdateBackend(
      sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider = { index }),
      eventsAnalyzer = JunieSessionEventsAnalyzer(sessionsRootPathProvider = { index.parent }),
    )

    val update = backend.buildSessionUpdate(
      FileBackedSessionChangeSet(changedPaths = setOf(index.parent.resolve("session-unmapped-events").resolve("events.jsonl")))
    )

    assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(update.scopedPaths).isNull()
    assertThat(update.threadIds).containsExactly("session-unmapped-events")
  }

  @Test
  fun `Junie backend emits scoped threads changed for index updates`() {
    val projectDir = tempDir.resolve("project-backend-index")
    val index = writeIndex(
      sessionIndexLine(
        sessionId = "session-backend-index",
        projectDir = projectDir,
        taskName = "Backend index",
        updatedAt = 3000L,
      ),
    )
    val backend = JunieSessionUpdateBackend(
      sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider = { index }),
      eventsAnalyzer = JunieSessionEventsAnalyzer(sessionsRootPathProvider = { index.parent }),
    )

    val update = backend.buildSessionUpdate(FileBackedSessionChangeSet(changedPaths = setOf(index)))

    assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(update.scopedPaths).containsExactly(projectDir.toString())
  }

  @Test
  fun `Junie backend includes explicit changed project file paths from events`() {
    val projectDir = tempDir.resolve("project-backend-changed-files")
    val changedFile = projectDir.resolve("src").resolve("Main.kt")
    val index = writeIndex(
      sessionIndexLine(
        sessionId = "session-backend-changed-files",
        projectDir = projectDir,
        taskName = "Backend changed files",
        updatedAt = 3000L,
      ),
    )
    writeJunieEvents(
      sessionsRoot = index.parent,
      sessionId = "session-backend-changed-files",
      sessionA2uxTerminalEvent(status = "COMPLETED", stepId = "step-1", changedPath = "src/Main.kt"),
    )
    val backend = JunieSessionUpdateBackend(
      sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider = { index }),
      eventsAnalyzer = JunieSessionEventsAnalyzer(sessionsRootPathProvider = { index.parent }),
    )

    val update = backend.buildSessionUpdate(
      FileBackedSessionChangeSet(changedPaths = setOf(index.parent.resolve("session-backend-changed-files").resolve("events.jsonl")))
    )

    assertThat(update.mayHaveChangedProjectFiles).isTrue()
    assertThat(update.changedProjectFilePaths).containsExactly(changedFile.toString())
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

  private fun sessionA2uxStatusEvent(status: String?): String {
    val statusField = status?.let { ",\"status\":\"${it.jsonEscape()}\"" }.orEmpty()
    return """
      {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentCurrentStatusUpdatedEvent"$statusField}}}
    """.trimIndent()
  }

  private fun sessionA2uxTerminalEvent(status: String, stepId: String, changedPath: String? = null): String {
    val changesField = changedPath?.let { ",\"changes\":[{\"path\":\"${it.jsonEscape()}\"}]" }.orEmpty()
    return """
      {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","stepId":"$stepId","status":"$status"$changesField}}}
    """.trimIndent()
  }

  private fun threadScopedRequest(projectDir: Path, threadId: String): AgentSessionSourceRefreshRequest {
    return AgentSessionSourceRefreshRequest(
      paths = listOf(projectDir.toString()),
      threadIds = setOf(threadId),
      updateEvent = AgentSessionSourceUpdateEvent.hintsChanged(
        scopedPaths = setOf(projectDir.toString()),
        threadIds = setOf(threadId),
      ),
    )
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
