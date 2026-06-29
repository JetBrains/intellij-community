// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActivityEvidence
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiSessionSourceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `loads sessions for matching project path`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-a")
      val otherProjectDir = tempDir.resolve("project-b")
      val sessionDir = tempDir.resolve("sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-old",
        cwd = projectDir,
        piUserMessageEntry(id = "user-old", content = "Old task", timestamp = 2_000L),
      )
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-new",
        cwd = projectDir,
        piUserMessageEntry(id = "user-new", content = "First user message", timestamp = 3_000L),
        piNamedSessionInfoEntry(name = "Named Pi session"),
      )
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-other",
        cwd = otherProjectDir,
        piUserMessageEntry(id = "user-other", content = "Other project", timestamp = 4_000L),
      )
      Files.writeString(sessionDir.resolve("malformed.jsonl"), "not json\n")
      val source = sourceFor(sessionDir)

      val threads = source.listThreads("$projectDir/", openProject = null)

      assertThat(threads.map { it.id }).containsExactly("session-new", "session-old")
      assertThat(threads[0].title).isEqualTo("Named Pi session")
      assertThat(threads[0].updatedAt).isEqualTo(3_000L)
      assertThat(threads[0].archived).isFalse()
      assertThat(threads[0].activityReport.rowActivity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads[0].provider).isEqualTo(PI_AGENT_SESSION_PROVIDER)
      assertThat(threads[1].title).isEqualTo("Old task")
    }
  }

  @Test
  fun `latest session info entry is used as title`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-latest-name")
      val sessionDir = tempDir.resolve("latest-name-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-latest-name",
        cwd = projectDir,
        piUserMessageEntry(id = "user-latest-name", content = "Fallback title", timestamp = 3_000L),
        piNamedSessionInfoEntry(id = "name-old", name = "Old name"),
        piNamedSessionInfoEntry(id = "name-new", name = "New name"),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.title).isEqualTo("New name")
    }
  }

  @Test
  fun `loads thread costs from pi assistant usage snapshots`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-usage")
      val sessionDir = tempDir.resolve("usage-sessions")
      val capturedUsages = ArrayList<AgentSessionUsageSnapshot>()
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-usage",
        cwd = projectDir,
        piUserMessageEntry(id = "user-usage", content = "Count usage", timestamp = 1_000L),
        piAssistantUsageMessageEntry(
          id = "assistant-usage-1",
          parentId = "user-usage",
          model = "gpt-5.5",
          input = 100,
          output = 20,
          cacheRead = 30,
          cacheWrite = 40,
          totalTokens = 250,
        ),
        piAssistantUsageMessageEntry(
          id = "assistant-usage-2",
          parentId = "assistant-usage-1",
          model = "gpt-5.4",
          input = 10,
          output = 0,
          cacheRead = 5,
          cacheWrite = 5,
          totalTokens = 50,
        ),
      )
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }),
        calculateCost = { usage ->
          capturedUsages += usage
          AgentSessionCost(
            amountUsd = BigDecimal.valueOf(usage.inputTokens + usage.outputTokens + usage.cacheReadTokens + usage.cacheWriteTokens),
            kind = AgentSessionCostKind.ESTIMATED,
            matchedModelId = usage.modelId,
          )
        },
      )

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
      val costs = source.loadThreadCosts(projectDir.toString(), listOf(thread))

      assertThat(costs).containsOnlyKeys("session-usage")
      assertThat(costs["session-usage"]?.kind).isEqualTo(AgentSessionCostKind.ESTIMATED)
      assertThat(costs["session-usage"]?.amountUsd).isEqualByComparingTo("240")
      assertThat(costs["session-usage"]?.matchedModelId).isNull()
      assertThat(capturedUsages).containsExactly(
        AgentSessionUsageSnapshot(
          modelId = "[pi] gpt-5.5",
          inputTokens = 100,
          outputTokens = 20,
          cacheReadTokens = 30,
          cacheWriteTokens = 40,
          requestCount = 1,
        ),
        AgentSessionUsageSnapshot(
          modelId = "[pi] gpt-5.4",
          inputTokens = 10,
          outputTokens = 30,
          cacheReadTokens = 5,
          cacheWriteTokens = 5,
          requestCount = 1,
        ),
      )
    }
  }

  @Test
  fun `ignores zero JetBrains Central usage cost for paid pi model pricing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-jbcentral-zero-cost")
      val sessionDir = tempDir.resolve("jbcentral-zero-cost-sessions")
      val capturedUsages = ArrayList<AgentSessionUsageSnapshot>()
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-jbcentral-zero-cost",
        cwd = projectDir,
        piUserMessageEntry(id = "user-jbcentral-zero-cost", content = "Count paid usage", timestamp = 1_000L),
        piAssistantUsageMessageEntry(
          id = "assistant-free-proxy",
          parentId = "user-jbcentral-zero-cost",
          provider = "proxy",
          model = "nemotron-ultra",
          input = 10,
          output = 2,
          cacheRead = 0,
          cacheWrite = 0,
          totalTokens = 12,
          costTotal = BigDecimal.ZERO,
        ),
        piAssistantUsageMessageEntry(
          id = "assistant-paid-jbcentral",
          parentId = "assistant-free-proxy",
          provider = PI_JBCENTRAL_PROVIDER_NAME,
          model = "gpt-5.5",
          input = 100,
          output = 20,
          cacheRead = 30,
          cacheWrite = 0,
          totalTokens = 150,
          costTotal = BigDecimal.ZERO,
        ),
      )
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }),
        calculateCost = { usage ->
          capturedUsages += usage
          if (usage.nativeExactCostUsd != null) {
            AgentSessionCost(
              amountUsd = usage.nativeExactCostUsd,
              kind = AgentSessionCostKind.EXACT,
              matchedModelId = usage.modelId,
            )
          }
          else {
            AgentSessionCost(
              amountUsd = BigDecimal.valueOf(42),
              kind = AgentSessionCostKind.ESTIMATED,
              matchedModelId = usage.modelId,
            )
          }
        },
      )

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
      val costs = source.loadThreadCosts(projectDir.toString(), listOf(thread))

      assertThat(costs["session-jbcentral-zero-cost"]?.amountUsd).isEqualByComparingTo("42")
      assertThat(costs["session-jbcentral-zero-cost"]?.kind).isEqualTo(AgentSessionCostKind.ESTIMATED)
      assertThat(capturedUsages).containsExactly(
        AgentSessionUsageSnapshot(
          modelId = "[pi] nemotron-ultra",
          inputTokens = 10,
          outputTokens = 2,
          requestCount = 1,
          nativeExactCostUsd = BigDecimal.ZERO,
        ),
        AgentSessionUsageSnapshot(
          modelId = "[pi] gpt-5.5",
          inputTokens = 100,
          outputTokens = 20,
          cacheReadTokens = 30,
          requestCount = 1,
        ),
      )
    }
  }

  @Test
  fun `keeps non zero JetBrains Central usage cost as exact cost`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-jbcentral-exact-cost")
      val sessionDir = tempDir.resolve("jbcentral-exact-cost-sessions")
      val capturedUsages = ArrayList<AgentSessionUsageSnapshot>()
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-jbcentral-exact-cost",
        cwd = projectDir,
        piUserMessageEntry(id = "user-jbcentral-exact-cost", content = "Count exact usage", timestamp = 1_000L),
        piAssistantUsageMessageEntry(
          id = "assistant-paid-jbcentral",
          parentId = "user-jbcentral-exact-cost",
          provider = PI_JBCENTRAL_PROVIDER_NAME,
          model = "gpt-5.5",
          input = 100,
          output = 20,
          cacheRead = 30,
          cacheWrite = 0,
          totalTokens = 150,
          costTotal = BigDecimal("0.5"),
        ),
      )
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }),
        calculateCost = { usage ->
          capturedUsages += usage
          if (usage.nativeExactCostUsd != null) {
            AgentSessionCost(
              amountUsd = usage.nativeExactCostUsd,
              kind = AgentSessionCostKind.EXACT,
              matchedModelId = usage.modelId,
            )
          }
          else {
            AgentSessionCost(
              amountUsd = BigDecimal.valueOf(42),
              kind = AgentSessionCostKind.ESTIMATED,
              matchedModelId = usage.modelId,
            )
          }
        },
      )

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
      val costs = source.loadThreadCosts(projectDir.toString(), listOf(thread))

      assertThat(costs["session-jbcentral-exact-cost"]?.amountUsd).isEqualByComparingTo("0.5")
      assertThat(costs["session-jbcentral-exact-cost"]?.kind).isEqualTo(AgentSessionCostKind.EXACT)
      assertThat(capturedUsages).containsExactly(
        AgentSessionUsageSnapshot(
          modelId = "[pi] gpt-5.5",
          inputTokens = 100,
          outputTokens = 20,
          cacheReadTokens = 30,
          requestCount = 1,
          nativeExactCostUsd = BigDecimal("0.5"),
        ),
      )
    }
  }

  @Test
  @RegistryKey(key = PI_FILE_WATCH_FALLBACK_REGISTRY_KEY, value = "false")
  fun `pi file watch fallback registry flag is disabled`() {
    assertThat(isPiFileWatchFallbackEnabled()).isFalse()
  }

  @Test
  @RegistryKey(key = PI_FILE_WATCH_FALLBACK_REGISTRY_KEY, value = "true")
  fun `pi file watch fallback registry flag can be enabled`() {
    assertThat(isPiFileWatchFallbackEnabled()).isTrue()
  }

  @Test
  fun `disabled file watch fallback does not resolve contributors`() {
    var contributorProviderCallCount = 0

    val source = PiSessionSource(
      sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }),
      extensionStatusEvents = emptyFlow(),
      fileWatchFallbackEnabledProvider = { false },
      sessionUpdateEventsContributorProvider = {
        contributorProviderCallCount++
        listOf(object : PiSessionUpdateEventsContributor {
          override fun createUpdateEvents(watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>) =
            error("Disabled file watch fallback must not create contributor flows")
        })
      },
    )

    assertThat(source).isInstanceOf(AgentSessionUpdateSource::class.java)
    assertThat(contributorProviderCallCount).isZero()
  }

  @Test
  fun `enabled file watch fallback merges contributor updates`() {
    runBlocking(Dispatchers.Default) {
      val updateEvent = AgentSessionSourceUpdateEvent.threadsChanged(
        scopedPaths = setOf(tempDir.resolve("project-contributor").toString()),
      )
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }),
        extensionStatusEvents = emptyFlow(),
        fileWatchFallbackEnabledProvider = { true },
        sessionUpdateEventsContributorProvider = {
          listOf(object : PiSessionUpdateEventsContributor {
            override fun createUpdateEvents(watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>) =
              flowOf(updateEvent)
          })
        },
      )

      assertThat(source.updateEvents.first { event -> event.type == AgentSessionSourceUpdate.THREADS_CHANGED }).isEqualTo(updateEvent)
    }
  }

  @Test
  fun `pi extension status update creates scoped activity hint`() {
    val projectStatusDir = tempDir.resolve("project-status")
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status","cwd":"${projectStatusDir.toString().jsonEscape()}","activity":"processing","updatedAt":5000}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.activityUpdatesByThreadId).containsOnlyKeys("session-status")
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status")?.activityReport).isEqualTo(
      AgentThreadActivityReport(AgentThreadActivity.PROCESSING)
    )
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status")?.updatedAt).isEqualTo(5_000L)
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status")?.evidence).isEqualTo(AgentSessionActivityEvidence.SEMANTIC)
    assertThat(updateEvent?.threadIds).containsExactly("session-status")
  }

  @Test
  fun `pi extension done status update uses shared unread activity`() {
    val projectStatusDir = tempDir.resolve("project-status-done")
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status-done","cwd":"${projectStatusDir.toString().jsonEscape()}","activity":"done","updatedAt":5000}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.threadIds).containsExactly("session-status-done")
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status-done")?.activityReport).isEqualTo(
      AgentThreadActivityReport(AgentThreadActivity.UNREAD)
    )
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status-done")?.updatedAt).isEqualTo(5_000L)
  }

  @Test
  fun `pi extension session info update creates scoped thread refresh`() {
    val projectStatusDir = tempDir.resolve("project-status-name")
    val projectStatusDirJson = projectStatusDir.toString().jsonString()
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status-name","cwd":$projectStatusDirJson,"event":"session_info_changed","name":"Renamed"}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.threadIds).containsExactly("session-status-name")
    assertThat(updateEvent?.activityUpdatesByThreadId).isEmpty()
  }

  @Test
  fun `array text content is used as first user message fallback title`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-array-content")
      val sessionDir = tempDir.resolve("array-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-array",
        cwd = projectDir,
        """
          {"type":"message","id":"user-1","parentId":null,"timestamp":"2026-01-01T00:00:02Z","message":{"role":"user","content":[{"type":"text","text":"First"},{"type":"image","source":"ignored"},{"type":"text","text":"task"}]}}
        """.trimIndent(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.title).isEqualTo("First task")
    }
  }

  @Test
  fun `loads thread outline from pi session jsonl tree`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-outline")
      val sessionDir = tempDir.resolve("outline-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-outline",
        cwd = projectDir,
        piUserMessageEntry(id = "user-outline", content = "Fix flaky test", timestamp = 1_000L),
        piLabelEntry(),
        piAssistantTextAndToolEntry(),
        piBashExecutionMessageEntry(),
        piNamedSessionInfoEntry(id = "name-outline", name = "Named outline"),
      )
      val source = sourceFor(sessionDir)

      val outline = source.loadThreadOutline(projectDir.toString(), "session-outline", null)

      assertThat(outline).isNotNull
      assertThat(outline!!.provider).isEqualTo(PI_AGENT_SESSION_PROVIDER)
      assertThat(outline.threadId).isEqualTo("session-outline")
      assertThat(outline.title).isEqualTo("Named outline")
      assertThat(outline.updatedAt).isEqualTo(3_000L)
      assertThat(outline.items.map { it.kind }).containsExactly(
        AgentSessionOutlineItemKind.USER_PROMPT,
        AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
      )
      val userPrompt = outline.items[0]
      assertThat(userPrompt.kind).isEqualTo(AgentSessionOutlineItemKind.USER_PROMPT)
      assertThat(userPrompt.preview).isEqualTo("Fix flaky test")
      assertThat(userPrompt.children).isEmpty()
      val assistant = outline.items[1]
      assertThat(assistant.kind).isEqualTo(AgentSessionOutlineItemKind.ASSISTANT_RESPONSE)
      assertThat(assistant.title).isEqualTo("I will inspect the failure.")
      assertThat(assistant.children.map { it.kind }).containsExactly(
        AgentSessionOutlineItemKind.TOOL_CALL,
        AgentSessionOutlineItemKind.TOOL_RESULT,
      )
      assertThat(assistant.children.map { it.title }).containsExactly("Read", "Exit 0")
      assertThat(assistant.children[0].preview).isEqualTo("src/Test.kt")
      assertThat(assistant.children[1].preview).isEqualTo("ok")
    }
  }

  @Test
  fun `forks outline item from local session file when live control is unavailable`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-local-fork")
      val sessionDir = tempDir.resolve("local-fork-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-local-fork",
        cwd = projectDir,
        piUserMessageEntry(id = "user-first", content = "First task", timestamp = 1_000L),
        piAssistantTextMessageEntry(id = "assistant-first", parentId = "user-first", content = "First answer", timestamp = 2_000L),
        piUserMessageEntry(id = "user-second", content = "Second task", timestamp = 3_000L, parentId = "assistant-first"),
        piAssistantTextMessageEntry(id = "assistant-second", parentId = "user-second", content = "Second answer", timestamp = 4_000L),
      )
      val source = sourceFor(sessionDir)

      assertThat(source.canForkThreadFromOutlineItem(projectDir.toString(),
                                                     "session-local-fork",
                                                     "assistant-first",
                                                     null,
                                                     "tab-1")).isTrue()

      val forkResult = source.forkThreadFromOutlineItem(
        project = ProjectManager.getInstance().defaultProject,
        path = projectDir.toString(),
        threadId = "session-local-fork",
        itemId = "assistant-first",
        subAgentId = null,
        tabKey = "tab-1",
      )

      val forkedThread = checkNotNull(forkResult?.thread)
      assertThat(forkedThread.provider).isEqualTo(PI_AGENT_SESSION_PROVIDER)
      assertThat(forkedThread.id).isNotEqualTo("session-local-fork")
      assertThat(forkedThread.title).isEqualTo("First task")
      val forkedOutline = source.loadThreadOutline(projectDir.toString(), forkedThread.id, null)
      assertThat(forkedOutline).isNotNull
      assertThat(forkedOutline!!.items.map { it.id }).containsExactly("user-first", "assistant-first")
    }
  }

  @Test
  fun `flattens linear pi conversation parent chain in outline`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-linear-outline")
      val sessionDir = tempDir.resolve("linear-outline-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-linear-outline",
        cwd = projectDir,
        piModelChangeEntry(id = "model-first", parentId = null),
        piUserMessageEntry(id = "user-first", content = "2 + 2?", timestamp = 1_000L, parentId = "model-first"),
        piAssistantTextMessageEntry(id = "assistant-first", parentId = "user-first", content = "2 + 2 = 4", timestamp = 2_000L),
        piModelChangeEntry(id = "model-second", parentId = "assistant-first"),
        piUserMessageEntry(id = "user-second", content = "5 + 5?=", timestamp = 3_000L, parentId = "model-second"),
        piAssistantTextMessageEntry(id = "assistant-second", parentId = "user-second", content = "5 + 5 = 10", timestamp = 4_000L),
      )
      val source = sourceFor(sessionDir)

      val outline = source.loadThreadOutline(projectDir.toString(), "session-linear-outline", null)

      assertThat(outline).isNotNull
      assertThat(outline!!.items.map { it.kind }).containsExactly(
        AgentSessionOutlineItemKind.USER_PROMPT,
        AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
        AgentSessionOutlineItemKind.USER_PROMPT,
        AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
      )
      assertThat(outline.items.map { it.preview }).containsExactly("2 + 2?", "2 + 2 = 4", "5 + 5?=", "5 + 5 = 10")
      assertThat(outline.items.flatMap { it.children }).isEmpty()
    }
  }

  @Test
  fun `archive and unarchive use session info title prefix`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archive")
      val sessionDir = tempDir.resolve("archive-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-archive",
        cwd = projectDir,
        piUserMessageEntry(id = "user-archive", content = "Archive me", timestamp = 3_000L),
      )
      var now = 4_000L
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { now })
      val source = PiSessionSource(sessionStore = store)

      assertThat(store.archiveThread(projectDir.toString(), "session-archive")).isTrue()
      assertThat(source.listThreads(projectDir.toString(), openProject = null)).isEmpty()
      val archivedThread = source.listArchivedThreads(projectDir.toString(), openProject = null).single()
      assertThat(archivedThread.id).isEqualTo("session-archive")
      assertThat(archivedThread.title).isEqualTo("Archive me")
      assertThat(archivedThread.archived).isTrue()
      assertThat(archivedThread.updatedAt).isEqualTo(3_000L)
      val sessionFile = sessionDir.resolve("2026-01-01T00-00-00-000Z_session-archive.jsonl")
      assertThat(Files.readString(sessionFile)).contains("\"name\":\"[archived] Archive me\"")

      now = 5_000L
      assertThat(store.unarchiveThread(projectDir.toString(), "session-archive")).isTrue()
      val activeThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(activeThread.title).isEqualTo("Archive me")
      assertThat(activeThread.archived).isFalse()
      assertThat(activeThread.updatedAt).isEqualTo(3_000L)
      assertThat(source.listArchivedThreads(projectDir.toString(), openProject = null)).isEmpty()
      assertThat(Files.readString(sessionFile)).contains("\"name\":\"Archive me\"")
      assertThat(Files.exists(sessionDir.resolve("agent-workbench-archive-state.jsonl"))).isFalse()
    }
  }

  @Test
  fun `archive and unarchive preserve processing activity`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archive-processing")
      val sessionDir = tempDir.resolve("archive-processing-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-archive-processing",
        cwd = projectDir,
        piUserMessageEntry(id = "user-archive-processing", content = "Still running", timestamp = 3_000L),
      )
      var now = 4_000L
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { now })
      val source = PiSessionSource(sessionStore = store)

      assertThat(source.listThreads(projectDir.toString(), openProject = null).single().activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(store.archiveThread(projectDir.toString(), "session-archive-processing")).isTrue()
      val archivedThread = source.listArchivedThreads(projectDir.toString(), openProject = null).single()
      assertThat(archivedThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)

      now = 5_000L
      assertThat(store.unarchiveThread(projectDir.toString(), "session-archive-processing")).isTrue()
      val activeThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(activeThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `rename appends pi session info entry`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rename")
      val sessionDir = tempDir.resolve("rename-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-rename",
        cwd = projectDir,
        piUserMessageEntry(id = "user-rename", content = "Old title", timestamp = 3_000L),
      )
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { 4_000L })
      val source = PiSessionSource(sessionStore = store)

      assertThat(store.renameThread(projectDir.toString(), "session-rename", "  New title  ")).isTrue()

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(thread.title).isEqualTo("New title")
      val lines = Files.readAllLines(sessionFile)
      assertThat(lines.last()).contains("\"type\":\"session_info\"")
      assertThat(lines.last()).contains("\"parentId\":\"user-rename\"")
      assertThat(lines.last()).contains("\"name\":\"New title\"")
    }
  }

  @Test
  fun `rename preserves pi archive title prefix`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rename-archived")
      val sessionDir = tempDir.resolve("rename-archived-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-rename-archived",
        cwd = projectDir,
        piUserMessageEntry(id = "user-rename-archived", content = "Old title", timestamp = 3_000L),
      )
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { 4_000L })
      val source = PiSessionSource(sessionStore = store)

      assertThat(store.archiveThread(projectDir.toString(), "session-rename-archived")).isTrue()
      assertThat(store.renameThread(projectDir.toString(), "session-rename-archived", "New title")).isTrue()

      val archivedThread = source.listArchivedThreads(projectDir.toString(), openProject = null).single()
      assertThat(archivedThread.title).isEqualTo("New title")
      assertThat(archivedThread.archived).isTrue()
      assertThat(Files.readAllLines(sessionFile).last()).contains("\"name\":\"[archived] New title\"")
    }
  }

  @Test
  fun `read tracker marks updated sessions unread`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-read-state")
      val sessionDir = tempDir.resolve("read-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-read",
        cwd = projectDir,
        piUserMessageEntry(id = "user-read", content = "Read state", timestamp = 2_000L),
        piAssistantMessageEntry(id = "assistant-read", parentId = "user-read", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      source.markThreadAsRead("session-read", 2_000L)
      assertThat(source.listThreads(projectDir.toString(), openProject = null).single().activityReport.rowActivity).isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("session-read", 3_000L)
      assertThat(source.listThreads(projectDir.toString(), openProject = null).single().activityReport.rowActivity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun `user leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-user-leaf")
      val sessionDir = tempDir.resolve("user-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-user-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-leaf", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `assistant tool use leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-tool-use-leaf")
      val sessionDir = tempDir.resolve("tool-use-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-tool-use-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-tool-use", content = "Run task", timestamp = 2_000L),
        piAssistantMessageEntry(
          id = "assistant-tool-use",
          parentId = "user-tool-use",
          timestamp = 3_000L,
          stopReason = "toolUse",
        ),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `tool result leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-tool-result-leaf")
      val sessionDir = tempDir.resolve("tool-result-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-tool-result-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-tool-result", content = "Run task", timestamp = 2_000L),
        piToolResultMessageEntry(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `custom message leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-custom-message-leaf")
      val sessionDir = tempDir.resolve("custom-message-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-custom-message-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-custom-message", content = "Run task", timestamp = 2_000L),
        piCustomMessageEntry(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreads(projectDir.toString(), openProject = null).single()

      assertThat(thread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `completed observed session is marked unread until read`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-completed-observed")
      val sessionDir = tempDir.resolve("completed-observed-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-completed-observed",
        cwd = projectDir,
        piUserMessageEntry(id = "user-completed-observed", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      val workingThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(workingThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
      appendPiSessionEntry(
        sessionFile,
        piAssistantMessageEntry(id = "assistant-completed-observed", parentId = "user-completed-observed", timestamp = 4_000L),
      )

      val completedThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(completedThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.UNREAD)
      source.markThreadAsRead("session-completed-observed", 4_000L)
      val readThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(readThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun `active working session completion is not premarked read`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-active-completed")
      val sessionDir = tempDir.resolve("active-completed-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-active-completed",
        cwd = projectDir,
        piUserMessageEntry(id = "user-active-completed", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)
      source.setActiveThreadId("session-active-completed")

      val workingThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(workingThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
      appendPiSessionEntry(
        sessionFile,
        piAssistantMessageEntry(id = "assistant-active-completed", parentId = "user-active-completed", timestamp = 4_000L),
      )

      val completedThread = source.listThreads(projectDir.toString(), openProject = null).single()
      assertThat(completedThread.activityReport.rowActivity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun `effective session dir follows pi precedence`() {
    val homeDir = tempDir.resolve("home")
    val projectDir = tempDir.resolve("project-settings")
    val globalSessionDir = tempDir.resolve("global-sessions")
    val projectSessionDir = tempDir.resolve("project-sessions")
    val envSessionDir = tempDir.resolve("env-sessions")
    Files.createDirectories(homeDir.resolve(".pi").resolve("agent"))
    Files.createDirectories(projectDir.resolve(".pi"))
    Files.writeString(homeDir.resolve(".pi").resolve("agent").resolve("settings.json"), "{\"sessionDir\":\"$globalSessionDir\"}")
    Files.writeString(projectDir.resolve(".pi").resolve("settings.json"), "{\"sessionDir\":\"$projectSessionDir\"}")

    val projectConfigured = resolveEffectivePiSessionDir(
      projectPath = projectDir.toString(),
      environmentProvider = { null },
      homeDirProvider = { homeDir.toString() },
    )
    val envConfigured = resolveEffectivePiSessionDir(
      projectPath = projectDir.toString(),
      environmentProvider = { key -> if (key == "PI_CODING_AGENT_SESSION_DIR") envSessionDir.toString() else null },
      homeDirProvider = { homeDir.toString() },
    )

    assertThat(projectConfigured).isEqualTo(projectSessionDir)
    assertThat(envConfigured).isEqualTo(envSessionDir)
  }

  @Test
  fun `outline fork action is shown only for concrete top level items`() {
    val projectDir = tempDir.resolve("project-fork-visibility")
    val sessionDir = tempDir.resolve("fork-visibility-sessions")
    writePiSession(
      sessionDir = sessionDir,
      sessionId = "session-fork-visibility",
      cwd = projectDir,
      piUserMessageEntry(id = "user-first", content = "First task", timestamp = 1_000L),
      piAssistantTextMessageEntry(id = "assistant-first", parentId = "user-first", content = "First answer", timestamp = 2_000L),
      piUserMessageEntry(id = "user-second", content = "Second task", timestamp = 3_000L, parentId = "assistant-first"),
    )
    val source = sourceFor(sessionDir)

    assertThat(
      source.canForkThreadFromOutlineItem(
        path = projectDir.toString(),
        threadId = "session-fork-visibility",
        itemId = "assistant-first",
        subAgentId = null,
        tabKey = "tab-1",
      )
    ).isTrue()
    assertThat(
      source.canForkThreadFromOutlineItem(
        path = projectDir.toString(),
        threadId = "session-fork-visibility",
        itemId = " ",
        subAgentId = null,
        tabKey = "tab-1",
      )
    ).isFalse()
    assertThat(
      source.canForkThreadFromOutlineItem(
        path = projectDir.toString(),
        threadId = "session-fork-visibility",
        itemId = "assistant-first",
        subAgentId = "alpha",
        tabKey = "tab-1",
      )
    ).isFalse()
  }

  private fun sourceFor(sessionDir: Path): PiSessionSource {
    return PiSessionSource(sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }))
  }

  private fun writePiSession(sessionDir: Path, sessionId: String, cwd: Path, vararg entries: String): Path {
    Files.createDirectories(sessionDir)
    val sessionFile = sessionDir.resolve("2026-01-01T00-00-00-000Z_$sessionId.jsonl")
    val header =
      """{"type":"session","version":3,"id":"$sessionId","timestamp":"2026-01-01T00:00:01Z","cwd":"${cwd.toString().jsonEscape()}"}"""
    Files.writeString(sessionFile, (listOf(header) + entries).joinToString(separator = "\n", postfix = "\n"))
    return sessionFile
  }

  private fun appendPiSessionEntry(sessionFile: Path, entry: String) {
    Files.writeString(sessionFile, entry + "\n", StandardOpenOption.APPEND)
  }

}

private fun piUserMessageEntry(id: String, content: String, timestamp: Long, parentId: String? = null): String {
  return piMessageEntry(
    id = id,
    parentId = parentId,
    entryTimestamp = "2026-01-01T00:00:02Z",
    messageFields = "\"role\":\"user\",\"content\":${content.jsonString()},\"timestamp\":$timestamp",
  )
}

private fun piAssistantTextMessageEntry(id: String, parentId: String, content: String, timestamp: Long): String {
  return piMessageEntry(
    id = id,
    parentId = parentId,
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = "\"role\":\"assistant\",\"content\":${content.jsonString()},\"timestamp\":$timestamp",
  )
}

private fun piAssistantMessageEntry(id: String, parentId: String, timestamp: Long, stopReason: String = "stop"): String {
  return piMessageEntry(
    id = id,
    parentId = parentId,
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = "\"role\":\"assistant\",\"content\":[],\"stopReason\":${stopReason.jsonString()},\"timestamp\":$timestamp",
  )
}

private fun piAssistantUsageMessageEntry(
  id: String,
  parentId: String,
  provider: String? = null,
  model: String,
  input: Long,
  output: Long,
  cacheRead: Long,
  cacheWrite: Long,
  totalTokens: Long,
  costTotal: BigDecimal? = null,
): String {
  val providerField = provider?.let { "\"provider\":${it.jsonString()}," }.orEmpty()
  val costField = costTotal?.let { ",\"cost\":{\"total\":${it.toPlainString()}}" }.orEmpty()
  val messageFields = """
    "role":"assistant",
    $providerField
    "model":${model.jsonString()},
    "content":[],
    "timestamp":2000,
    "usage":{
      "input":$input,
      "output":$output,
      "cacheRead":$cacheRead,
      "cacheWrite":$cacheWrite,
      "totalTokens":$totalTokens$costField
    }
  """.trimIndent().replace("\n", "")
  return piMessageEntry(
    id = id,
    parentId = parentId,
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = messageFields,
  )
}

private fun piToolResultMessageEntry(): String {
  return piMessageEntry(
    id = "tool-result",
    parentId = "user-tool-result",
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = "\"role\":\"toolResult\",\"content\":[],\"timestamp\":3000",
  )
}

private fun piAssistantTextAndToolEntry(): String {
  val messageFields = """
    "role":"assistant",
    "content":[
      {"type":"text","text":"I will inspect the failure."},
      {"type":"tool_use","id":"tool-read","name":"Read","input":{"file_path":"src/Test.kt"}}
    ],
    "timestamp":2000
  """.trimIndent().replace("\n", "")
  return piMessageEntry(
    id = "assistant-outline",
    parentId = "label-outline",
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = messageFields,
  )
}

private fun piBashExecutionMessageEntry(): String {
  val messageFields = """
    "role":"bashExecution",
    "content":{"type":"bashExecution","text":"ok","exitCode":0},
    "timestamp":3000
  """.trimIndent().replace("\n", "")
  return piMessageEntry(
    id = "result-outline",
    parentId = "assistant-outline",
    entryTimestamp = "2026-01-01T00:00:04Z",
    messageFields = messageFields,
  )
}

private fun piLabelEntry(): String {
  return listOf(
    "\"type\":\"label\"",
    "\"id\":\"label-outline\"",
    "\"parentId\":\"user-outline\"",
    "\"timestamp\":\"2026-01-01T00:00:02Z\"",
    "\"label\":\"hidden\"",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piModelChangeEntry(id: String, parentId: String?): String {
  return listOf(
    "\"type\":\"model_change\"",
    "\"id\":${id.jsonString()}",
    "\"parentId\":${parentId?.jsonString() ?: "null"}",
    "\"timestamp\":\"2026-01-01T00:00:02Z\"",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piCustomMessageEntry(): String {
  return listOf(
    "\"type\":\"custom_message\"",
    "\"id\":\"custom-message\"",
    "\"parentId\":\"user-custom-message\"",
    "\"timestamp\":\"2026-01-01T00:00:03Z\"",
    "\"customType\":\"status\"",
    "\"content\":\"working\"",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piNamedSessionInfoEntry(id: String = "name-new", name: String): String {
  return listOf(
    "\"type\":\"session_info\"",
    "\"id\":${id.jsonString()}",
    "\"parentId\":\"user-new\"",
    "\"timestamp\":\"2026-01-01T00:00:04Z\"",
    "\"name\":${name.jsonString()}",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piMessageEntry(id: String, parentId: String?, entryTimestamp: String, messageFields: String): String {
  return listOf(
    "\"type\":\"message\"",
    "\"id\":${id.jsonString()}",
    "\"parentId\":${parentId?.jsonString() ?: "null"}",
    "\"timestamp\":${entryTimestamp.jsonString()}",
    "\"message\":{$messageFields}",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun String.jsonEscape(): String {
  return replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun String.jsonString(): String {
  return "\"${jsonEscape()}\""
}
