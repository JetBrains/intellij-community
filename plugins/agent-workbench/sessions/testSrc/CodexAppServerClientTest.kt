// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationRouting
import com.intellij.agent.workbench.codex.common.CodexAppServerValue
import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionCandidate
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionContextItem
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionContextTruncation
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CodexAppServerClientTest {
  companion object {
    @JvmStatic
    fun backends(): List<CodexBackend> {
      return listOf(
        createMockBackendDefinition(),
        createRealBackendDefinition(),
      )
    }
  }

  @TempDir
  lateinit var tempDir: Path

  // Start the collector eagerly in tests that assert a notification from a single request.
  // That keeps the request under test from racing with collector startup.
  private fun CoroutineScope.awaitNextNotification(client: CodexAppServerClient) = async(start = CoroutineStart.UNDISPATCHED) {
    withTimeout(5.seconds) {
      client.notifications.first()
    }
  }

  // Keep negative assertions eagerly subscribed as well so unexpected notifications are observed
  // inside the timeout window instead of racing past collector startup.
  private fun CoroutineScope.awaitNoNotification(client: CodexAppServerClient) = async(start = CoroutineStart.UNDISPATCHED) {
    withTimeoutOrNull(500.milliseconds) {
      client.notifications.firstOrNull()
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("backends")
  fun listThreadsUsesCodexAppServerBackends(backend: CodexBackend): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(id = "thread-1", title = "First session", updatedAt = 1_700_000_000_000L, archived = false),
        ThreadSpec(id = "thread-2", title = "Second session", updatedAt = 1_700_000_010_000L, archived = false),
        ThreadSpec(id = "thread-3", title = "Archived session", updatedAt = 1_699_999_000_000L, archived = true),
      )
    )
    backend.run(scope = this, tempDir = tempDir, configPath = configPath)
  }

  @Test
  fun listThreadsFiltersThreadsByCwdFilter(): Unit = runBlocking(Dispatchers.Default) {
    val projectA = tempDir.resolve("project-alpha")
    val projectB = tempDir.resolve("project-beta")
    Files.createDirectories(projectA)
    Files.createDirectories(projectB)
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "alpha-1",
          title = "Alpha",
          cwd = projectA.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "beta-1",
          title = "Beta",
          cwd = projectB.toString(),
          updatedAt = 1_700_000_100_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-cwd")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val threadsA = client.listThreads(archived = false, cwdFilter = projectA.toString().replace('\\', '/').trimEnd('/'))
      val threadsB = client.listThreads(archived = false, cwdFilter = projectB.toString().replace('\\', '/').trimEnd('/'))
      val threadsAll = client.listThreads(archived = false)
      assertThat(threadsA.map { it.id }).containsExactly("alpha-1")
      assertThat(threadsB.map { it.id }).containsExactly("beta-1")
      assertThat(threadsAll.map { it.id }).containsExactlyInAnyOrder("alpha-1", "beta-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsIncludesSubAgentSourcesAndParsesSourceStatusMetadata(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-source-status")
    Files.createDirectories(project)
    val normalizedCwd = project.toString().replace('\\', '/').trimEnd('/')
    val configPath = tempDir.resolve("codex-source-status.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "parent-1",
          title = "Parent thread",
          cwd = normalizedCwd,
          sourceKind = "cli",
          statusType = "idle",
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-1",
          title = "Sub-agent thread",
          cwd = normalizedCwd,
          sourceKind = "subAgentThreadSpawn",
          parentThreadId = "parent-1",
          agentNickname = "Scout",
          agentRole = "reviewer",
          statusType = "active",
          activeFlags = listOf("waitingOnUserInput"),
          gitBranch = "feature/subagent",
          updatedAt = 1_700_000_010_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-source-status")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val threads = client.listThreads(archived = false, cwdFilter = normalizedCwd)
      assertThat(threads.map { it.id }).containsExactly("subagent-1", "parent-1")

      val byId = threads.associateBy { it.id }
      val parent = byId.getValue("parent-1")
      assertThat(parent.sourceKind).isEqualTo(CodexThreadSourceKind.CLI)
      assertThat(parent.statusKind).isEqualTo(CodexThreadStatusKind.IDLE)

      val subAgent = byId.getValue("subagent-1")
      assertThat(subAgent.sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN)
      assertThat(subAgent.parentThreadId).isEqualTo("parent-1")
      assertThat(subAgent.agentNickname).isEqualTo("Scout")
      assertThat(subAgent.agentRole).isEqualTo("reviewer")
      assertThat(subAgent.statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
      assertThat(subAgent.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_USER_INPUT)
      assertThat(subAgent.gitBranch).isEqualTo("feature/subagent")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsParsesSourceAndStatusVariantFormats(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-source-status-variants")
    Files.createDirectories(project)
    val normalizedCwd = project.toString().replace('\\', '/').trimEnd('/')
    val configPath = tempDir.resolve("codex-source-status-variants.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "parent-1",
          title = "Parent",
          cwd = normalizedCwd,
          sourceKind = "cli",
          statusType = "idle",
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-lowercase-source",
          title = "Lowercase subagent source",
          cwd = normalizedCwd,
          sourceKind = "subAgentThreadSpawn",
          sourceSubAgentFieldName = "subagent",
          parentThreadId = "parent-1",
          statusType = "active",
          statusActiveFlagsFieldName = "active_flags",
          activeFlags = listOf("waiting_on_approval"),
          updatedAt = 1_700_000_020_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-flat",
          title = "Flat subagent source",
          cwd = normalizedCwd,
          sourceKind = "subAgent",
          sourceAsString = true,
          statusType = "ACTIVE",
          activeFlags = listOf("waiting_on_user_input"),
          updatedAt = 1_700_000_019_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-flat-review",
          title = "Flat review source",
          cwd = normalizedCwd,
          sourceKind = "subAgentReview",
          sourceAsString = true,
          statusType = "idle",
          updatedAt = 1_700_000_018_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-flat-compact",
          title = "Flat compact source",
          cwd = normalizedCwd,
          sourceKind = "subAgentCompact",
          sourceAsString = true,
          statusType = "idle",
          updatedAt = 1_700_000_017_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "subagent-flat-other",
          title = "Flat other source",
          cwd = normalizedCwd,
          sourceKind = "subAgentOther",
          sourceAsString = true,
          statusType = "idle",
          updatedAt = 1_700_000_016_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "status-system-error",
          title = "System error status",
          cwd = normalizedCwd,
          sourceKind = "cli",
          statusType = "SYSTEM-ERROR",
          updatedAt = 1_700_000_015_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-source-status-variants")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val threads = client.listThreads(archived = false, cwdFilter = normalizedCwd)
      val byId = threads.associateBy { it.id }

      val lowercaseSource = byId.getValue("subagent-lowercase-source")
      assertThat(lowercaseSource.sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN)
      assertThat(lowercaseSource.parentThreadId).isEqualTo("parent-1")
      assertThat(lowercaseSource.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_APPROVAL)

      assertThat(byId.getValue("subagent-flat").sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT)
      assertThat(byId.getValue("subagent-flat-review").sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_REVIEW)
      assertThat(byId.getValue("subagent-flat-compact").sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_COMPACT)
      assertThat(byId.getValue("subagent-flat-other").sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_OTHER)
      assertThat(byId.getValue("subagent-flat").statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
      assertThat(byId.getValue("subagent-flat").activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_USER_INPUT)
      assertThat(byId.getValue("status-system-error").statusKind).isEqualTo(CodexThreadStatusKind.SYSTEM_ERROR)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readThreadActivitySnapshotParsesUnreadReviewAndInProgressSignals(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-thread-read-activity")
    Files.createDirectories(project)
    val normalizedCwd = project.toString().replace('\\', '/').trimEnd('/')
    val configPath = tempDir.resolve("codex-thread-read-activity.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-read-1",
          title = "Thread read activity",
          cwd = normalizedCwd,
          sourceKind = "appServer",
          statusType = "active",
          activeFlags = listOf("waitingOnApproval"),
          updatedAt = 1_700_000_030_000L,
          archived = false,
          readTurns = listOf(
            ThreadTurnSpec(
              statusType = "completed",
              itemTypes = listOf("userMessage", "agentMessage", "enteredReviewMode"),
            ),
            ThreadTurnSpec(
              statusType = "in_progress",
              statusAsObject = true,
              itemTypes = listOf("agentMessage"),
            ),
          ),
        ),
      )
    )
    val backendDir = tempDir.resolve("backend-thread-read-activity")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val snapshot = client.readThreadActivitySnapshot("thread-read-1")
      assertThat(snapshot).isNotNull
      assertThat(snapshot!!.threadId).isEqualTo("thread-read-1")
      assertThat(snapshot.statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
      assertThat(snapshot.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_APPROVAL)
      assertThat(snapshot.hasUnreadAssistantMessage).isTrue()
      assertThat(snapshot.isReviewing).isTrue()
      assertThat(snapshot.hasInProgressTurn).isTrue()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readThreadActivitySnapshotReturnsNullForUnknownThread() = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-thread-read-missing")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-thread-read-missing.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-existing",
          title = "Existing",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_040_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-thread-read-missing")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val snapshot = client.readThreadActivitySnapshot("thread-missing")
      assertThat(snapshot).isNull()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsAreExposedAsFlowEvents(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-1",
          title = "Thread notify",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_050_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PUBLIC_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-1",
      ),
    )
    try {
      val notification = awaitNextNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-notify-1")
      assertThat(snapshot).isNotNull

      val event = notification.await()
      assertThat(event.method).isEqualTo("thread/status/changed")
      assertThat(event.threadId).isEqualTo("thread-notify-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsRemainAvailableUntilCollectorStarts(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-buffered")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-buffered.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-buffered",
          title = "Thread notify buffered",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_050_500L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-buffered")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-buffered",
      ),
    )
    try {
      val snapshot = client.readThreadActivitySnapshot("thread-notify-buffered")
      assertThat(snapshot).isNotNull

      val event = withTimeout(2.seconds) {
        client.notifications.first()
      }
      assertThat(event.method).isEqualTo("thread/status/changed")
      assertThat(event.threadId).isEqualTo("thread-notify-buffered")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsQueueBurstBeforeCollection(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-burst")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-burst.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-burst",
          title = "Thread notify burst",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_050_600L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-burst")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-burst",
      ),
    )
    try {
      repeat(3) {
        val snapshot = client.readThreadActivitySnapshot("thread-notify-burst")
        assertThat(snapshot).isNotNull
      }

      val events = buildList {
        repeat(3) {
          add(withTimeout(2.seconds) {
            client.notifications.first()
          })
        }
      }
      assertThat(events).hasSize(3)
      assertThat(events.map { it.method }).containsOnly("thread/status/changed")
      assertThat(events.map { it.threadId }).containsOnly("thread-notify-burst")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsParseThreadIdSnakeCaseField(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-snake")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-snake.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-snake",
          title = "Thread notify snake",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_051_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-snake")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-snake",
        "CODEX_TEST_NOTIFY_THREAD_ID_STYLE" to "thread_id",
      ),
    )
    try {
      val notification = awaitNextNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-notify-snake")
      assertThat(snapshot).isNotNull

      val event = notification.await()
      assertThat(event.threadId).isEqualTo("thread-notify-snake")
      assertThat(event.method).isEqualTo("thread/status/changed")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsParseThreadIdFromNestedThreadObject(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-thread-object")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-thread-object.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-object",
          title = "Thread notify object",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_052_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-thread-object")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-object",
        "CODEX_TEST_NOTIFY_THREAD_ID_STYLE" to "thread_object",
      ),
    )
    try {
      val notification = awaitNextNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-notify-object")
      assertThat(snapshot).isNotNull

      val event = notification.await()
      assertThat(event.threadId).isEqualTo("thread-notify-object")
      assertThat(event.method).isEqualTo("thread/status/changed")
      assertThat(event.startedThread).isNull()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerStartedNotificationsExposeParsedThreadPayload(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-started-thread")
    Files.createDirectories(project)
    val normalizedCwd = project.toString().replace('\\', '/').trimEnd('/')
    val configPath = tempDir.resolve("codex-notifications-started-thread.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-started-object",
          title = "Thread started object",
          cwd = normalizedCwd,
          sourceKind = "appServer",
          statusType = "active",
          activeFlags = listOf("waitingOnApproval"),
          updatedAt = 1_700_000_054_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-started-thread")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/started",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-started-object",
        "CODEX_TEST_NOTIFY_THREAD_ID_STYLE" to "thread_object",
      ),
    )
    try {
      val notification = awaitNextNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-started-object")
      assertThat(snapshot).isNotNull

      val event = notification.await()
      assertThat(event.method).isEqualTo("thread/started")
      assertThat(event.threadId).isEqualTo("thread-started-object")
      assertThat(event.startedThread).isNotNull
      assertThat(event.startedThread?.title).isEqualTo("Thread started object")
      assertThat(event.startedThread?.cwd).isEqualTo(normalizedCwd)
      assertThat(event.startedThread?.statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
      assertThat(event.startedThread?.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_APPROVAL)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun appServerNotificationsWithIdAreIgnored(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-with-id")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-with-id.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-with-id",
          title = "Thread notify with id",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_053_000L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-with-id")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-with-id",
        "CODEX_TEST_NOTIFY_ID" to "notification-id-1",
      ),
    )
    try {
      val notification = awaitNoNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-notify-with-id")
      assertThat(snapshot).isNotNull

      assertThat(notification.await()).isNull()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun parsedOnlyRoutingDoesNotExposePublicNotifications(): Unit = runBlocking(Dispatchers.Default) {
    val project = tempDir.resolve("project-notifications-parsed-only")
    Files.createDirectories(project)
    val configPath = tempDir.resolve("codex-notifications-parsed-only.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-notify-parsed-only",
          title = "Thread notify parsed only",
          cwd = project.toString(),
          statusType = "idle",
          updatedAt = 1_700_000_053_500L,
          archived = false,
        )
      )
    )
    val backendDir = tempDir.resolve("backend-notifications-parsed-only")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_NOTIFY_METHOD" to "thread/status/changed",
        "CODEX_TEST_NOTIFY_ON_METHOD" to "thread/read",
        "CODEX_TEST_NOTIFY_THREAD_ID" to "thread-notify-parsed-only",
      ),
    )
    try {
      val notification = awaitNoNotification(client)

      val snapshot = client.readThreadActivitySnapshot("thread-notify-parsed-only")
      assertThat(snapshot).isNotNull

      assertThat(notification.await()).isNull()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsPageSupportsCursorAndLimit(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-page")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(id = "thread-1", title = "Thread 1", cwd = workingDir.toString(), updatedAt = 1_700_000_005_000L, archived = false),
        ThreadSpec(id = "thread-2", title = "Thread 2", cwd = workingDir.toString(), updatedAt = 1_700_000_004_000L, archived = false),
        ThreadSpec(id = "thread-3", title = "Thread 3", cwd = workingDir.toString(), updatedAt = 1_700_000_003_000L, archived = false),
        ThreadSpec(id = "thread-4", title = "Thread 4", cwd = workingDir.toString(), updatedAt = 1_700_000_002_000L, archived = false),
        ThreadSpec(id = "thread-5", title = "Thread 5", cwd = workingDir.toString(), updatedAt = 1_700_000_001_000L, archived = false),
      )
    )
    val backendDir = tempDir.resolve("backend-page")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val cwdFilter = workingDir.toString().replace('\\', '/').trimEnd('/')
      val first = client.listThreadsPage(archived = false, cursor = null, limit = 2, cwdFilter = cwdFilter)
      assertThat(first.threads.map { it.id }).containsExactly("thread-1", "thread-2")
      assertThat(first.nextCursor).isEqualTo("2")

      val second = client.listThreadsPage(archived = false, cursor = first.nextCursor, limit = 2, cwdFilter = cwdFilter)
      assertThat(second.threads.map { it.id }).containsExactly("thread-3", "thread-4")
      assertThat(second.nextCursor).isEqualTo("4")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsTraversesAllPagesBeyondTenPageBoundary(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-many-pages")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    val totalThreads = 520
    val threadSpecs = (1..totalThreads).map { index ->
      ThreadSpec(
        id = "thread-$index",
        title = "Thread $index",
        cwd = workingDir.toString(),
        updatedAt = 1_700_000_600_000L - index,
        archived = false,
      )
    }
    writeConfig(path = configPath, threads = threadSpecs)
    val backendDir = tempDir.resolve("backend-many-pages")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val cwdFilter = workingDir.toString().replace('\\', '/').trimEnd('/')
      val threads = client.listThreads(archived = false, cwdFilter = cwdFilter)
      assertThat(threads).hasSize(totalThreads)
      assertThat(threads.first().id).isEqualTo("thread-1")
      assertThat(threads.last().id).isEqualTo("thread-$totalThreads")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsParsesPreviewAndTimestampVariants(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-preview")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    val longPreview = "x".repeat(160)
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-preview",
          preview = longPreview,
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000L,
          updatedAtField = "updated_at",
          archived = false,
        ),
        ThreadSpec(
          id = "thread-name",
          name = "Named thread",
          cwd = workingDir.toString(),
          createdAt = 1_700_000_500_000L,
          createdAtField = "createdAt",
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-preview")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val cwdFilter = workingDir.toString().replace('\\', '/').trimEnd('/')
      val threads = client.listThreads(archived = false, cwdFilter = cwdFilter)
      val threadsById = threads.associateBy { it.id }
      val previewThread = threadsById.getValue("thread-preview")
      assertThat(previewThread.updatedAt).isEqualTo(1_700_000_000_000L)
      assertThat(previewThread.title).endsWith("...")
      assertThat(previewThread.title.length).isLessThan(longPreview.length)
      val namedThread = threadsById.getValue("thread-name")
      assertThat(namedThread.title).isEqualTo("Named thread")
      assertThat(namedThread.updatedAt).isEqualTo(1_700_000_500_000L)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsFailsOnServerError(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-err",
          title = "Thread",
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-error")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_ERROR_METHOD" to "thread/list",
        "CODEX_TEST_ERROR_MESSAGE" to "boom",
      ),
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("boom")
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadStartsNewThreadAndAddsItToList(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-start")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-old",
          title = "Old Thread",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-start")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val created = client.createThread()
      assertThat(created.id).startsWith("thread-start-")
      assertThat(created.archived).isFalse()
      assertThat(created.title).isNotBlank()

      val active = client.listThreads(archived = false)
      assertThat(active.first().id).isEqualTo(created.id)
      assertThat(active.map { it.id }).contains("thread-old")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadWithParamsPassesThemThrough(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-start-params")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-start-params")
    Files.createDirectories(backendDir)
    val requestPayloadLogPath = backendDir.resolve("thread-start-params-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_PAYLOAD_LOG" to requestPayloadLogPath.toString(),
      ),
    )
    try {
      val created = client.createThread(
        cwd = workingDir.toString(),
        approvalPolicy = "on-request",
        sandbox = "workspace-write",
      )
      assertThat(created.id).startsWith("thread-start-")
      assertThat(created.archived).isFalse()

      val payloadLog = Files.readString(requestPayloadLogPath)
      assertThat(payloadLog).contains("\"method\":\"thread/start\"")
      assertThat(payloadLog).contains("\"cwd\":\"$workingDir\"")
      assertThat(payloadLog).contains("\"approvalPolicy\":\"on-request\"")
      assertThat(payloadLog).contains("\"sandbox\":\"workspace-write\"")
      assertThat(payloadLog).doesNotContain("\"experimentalRawEvents\"")
      assertThat(payloadLog).doesNotContain("\"persistExtendedHistory\"")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun archiveThreadMovesThreadFromActiveToArchivedList(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-archive")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "thread-2",
          title = "Thread 2",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_004_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-archive")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val beforeArchive = client.listThreads(archived = false)
      assertThat(beforeArchive.map { it.id }).contains("thread-1", "thread-2")

      client.archiveThread("thread-1")

      val active = client.listThreads(archived = false)
      val archived = client.listThreads(archived = true)
      assertThat(active.map { it.id }).doesNotContain("thread-1")
      assertThat(archived.map { it.id }).contains("thread-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun unarchiveThreadMovesThreadFromArchivedToActiveList(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-unarchive")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = true,
        ),
        ThreadSpec(
          id = "thread-2",
          title = "Thread 2",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_004_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-unarchive")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val beforeUnarchive = client.listThreads(archived = true)
      assertThat(beforeUnarchive.map { it.id }).contains("thread-1")

      client.unarchiveThread("thread-1")

      val active = client.listThreads(archived = false)
      val archived = client.listThreads(archived = true)
      assertThat(active.map { it.id }).contains("thread-1", "thread-2")
      assertThat(archived.map { it.id }).doesNotContain("thread-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun idleTimeoutStopsLazyStartedProcess(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-idle-timeout")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-idle-timeout")
    Files.createDirectories(backendDir)
    val marker = backendDir.resolve("cwd-marker.txt")
    val codexShim = createMockCodexShim(backendDir, configPath)
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { codexShim.toString() },
      environmentOverrides = mapOf("CODEX_TEST_CWD_MARKER" to marker.toString()),
      idleShutdownTimeoutMs = 100,
    )
    try {
      client.listThreads(archived = false)
      assertThat(Files.exists(marker)).isTrue()
      Files.deleteIfExists(marker)

      delay(250.milliseconds)
      client.listThreads(archived = false)

      assertThat(Files.exists(marker))
        .describedAs("marker file should be recreated when app-server restarts after idle timeout")
        .isTrue()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun persistThreadSendsTurnStartWithoutInterrupt(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-persist")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
      ),
    )
    try {
      val created = client.createThread()
      client.persistThread(created.id)

      val methods = Files.readAllLines(requestLogPath)
      assertThat(methods).contains("turn/start")
      assertThat(methods).doesNotContain("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptUsesRealTurnFlowAndReturnsGeneratedCandidates(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest")
    Files.createDirectories(backendDir)
    val requestPayloadLogPath = backendDir.resolve("prompt-suggest-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_PAYLOAD_LOG" to requestPayloadLogPath.toString(),
      ),
    )
    try {
      val candidates = client.suggestPrompt(createPromptSuggestionRequest())

      assertThat(candidates).isEqualTo(expectedGeneratedPromptSuggestionResult())

      val payloadLog = Files.readString(requestPayloadLogPath)
      assertThat(payloadLog).contains("\"method\":\"thread/start\"")
      assertThat(payloadLog).contains("\"cwd\":\"/work/project\"")
      assertThat(payloadLog).contains("\"approvalPolicy\":\"never\"")
      assertThat(payloadLog).contains("\"sandbox\":\"read-only\"")
      assertThat(payloadLog).contains("\"ephemeral\":true")
      assertThat(payloadLog).contains("\"experimentalRawEvents\":false")
      assertThat(payloadLog).contains("\"persistExtendedHistory\":false")
      assertThat(payloadLog).contains("\"method\":\"turn/start\"")
      assertThat(payloadLog).contains("\"model\":\"gpt-5.4\"")
      assertThat(payloadLog).contains("\"effort\":\"low\"")
      assertThat(payloadLog).contains("\"outputSchema\":{\"type\":\"object\"")
      assertThat(payloadLog).contains("\"required\":[\"id\",\"label\",\"promptText\"]")
      assertThat(payloadLog).contains("\"id\":{\"type\":[\"string\",\"null\"]}")
      assertThat(payloadLog).contains("Target mode: new_task")
      assertThat(payloadLog).contains("Visible context items (1):")
      assertThat(payloadLog).contains("rendererId: testFailures")
      assertThat(payloadLog).contains("itemId: failure-1")
      assertThat(payloadLog).contains("parentItemId: suite-1")
      assertThat(payloadLog).contains("source: testRunner")
      assertThat(payloadLog).contains("truncation: reason=source_limit, includedChars=480, originalChars=1200")
      assertThat(payloadLog).contains("Fallback seed candidates (3):")
      assertThat(payloadLog).contains("id: tests.fix")
      assertThat(payloadLog).contains("id: tests.explain")
      assertThat(payloadLog).contains("id: tests.stabilize")
      assertThat(payloadLog).doesNotContain("\"method\":\"prompt/suggest\"")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptUsesRealCodexAppServerWithStrictOutputSchema(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-real")
    Files.createDirectories(backendDir)
    createRealPromptSuggestionHarness(
      scope = this,
      tempDir = backendDir,
      responsePlans = listOf(
        MockResponsesPlan.completedAssistantMessage(renderGeneratedPromptSuggestionPayload())
      ),
    ).use { harness ->
      val suggestions = harness.client.suggestPrompt(
        createPromptSuggestionRequest(
          cwd = harness.projectDir.toString(),
          model = "mock-model",
        )
      )

      assertThat(suggestions).isEqualTo(expectedGeneratedPromptSuggestionResult())
      val requestBody = harness.responsesServer.requests().single()
      assertThat(requestBody).contains("\"strict\":true")
      assertThat(requestBody).contains("\"required\":[\"id\",\"label\",\"promptText\"]")
      assertThat(requestBody).contains("\"id\":{\"type\":[\"string\",\"null\"]}")
    }
  }

  @Test
  fun suggestPromptParsesPolishedSeedResponses(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-polished")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_PROMPT_SUGGEST_KIND" to "polished",
      ),
    )
    try {
      assertThat(client.suggestPrompt(createPromptSuggestionRequest())).isEqualTo(
        CodexPromptSuggestionResult.PolishedSeeds(
          listOf(
            CodexPromptSuggestionCandidate(
              id = "tests.fix",
              label = "AI: Fix the ParserTest failure",
              promptText = "Investigate ParserTest, identify the root cause, and implement the minimal fix.",
            ),
            CodexPromptSuggestionCandidate(
              id = "tests.explain",
              label = "AI: Explain the ParserTest failure",
              promptText = "Explain why ParserTest is failing and point out the relevant code path.",
            ),
            CodexPromptSuggestionCandidate(
              id = "tests.stabilize",
              label = "AI: Stabilize the ParserTest coverage",
              promptText = "Stabilize the ParserTest scenario and call out any missing assertions or cleanup.",
            ),
          )
        )
      )
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadFailsOnServerError(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-start-error")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_ERROR_METHOD" to "thread/start",
        "CODEX_TEST_ERROR_MESSAGE" to "boom",
      ),
    )
    try {
      try {
        client.createThread()
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("boom")
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptSendsInterruptWhenCancelledBeforeTerminalCompletion(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-timeout")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-timeout-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt",
      ),
    )
    try {
      val suggestion = async(start = CoroutineStart.UNDISPATCHED) {
        client.suggestPrompt(createPromptSuggestionRequest())
      }

      withTimeout(5.seconds) {
        while (!Files.exists(requestLogPath) || !Files.readAllLines(requestLogPath).contains("turn/start")) {
          delay(10.milliseconds)
        }
      }

      suggestion.cancel()
      try {
        suggestion.await()
        fail("Expected CancellationException")
      }
      catch (_: CancellationException) {
      }

      assertThat(Files.readAllLines(requestLogPath)).contains("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptReturnsNullWhenParentTimeoutCancelsGeneration(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-parent-timeout")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-parent-timeout-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt",
      ),
    )
    try {
      assertThat(client.listThreads(archived = false)).isEmpty()

      assertThat(withTimeoutOrNull(500.milliseconds) {
        client.suggestPrompt(createPromptSuggestionRequest())
      }).isNull()

      assertThat(Files.readAllLines(requestLogPath)).contains("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptKeepsClientWhenInterruptResponseIsMissingButTerminalCompletionArrives(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-missing-interrupt-response")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-missing-interrupt-response-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt_without_response,completed",
      ),
    )
    try {
      assertThat(client.listThreads(archived = false)).isEmpty()

      assertThat(withTimeoutOrNull(500.milliseconds) {
        client.suggestPrompt(createPromptSuggestionRequest())
      }).isNull()

      assertThat(client.suggestPrompt(createPromptSuggestionRequest())).isEqualTo(expectedGeneratedPromptSuggestionResult())

      val methods = Files.readAllLines(requestLogPath)
      assertThat(methods).contains("turn/interrupt")
      assertThat(methods.count { it == "initialize" }).isEqualTo(1)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptResetsClientWhenInterruptDoesNotReachTerminalCompletion(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-missing-terminal")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-missing-terminal-requests.log")
    val lifecycleStatePath = backendDir.resolve("prompt-suggest-missing-terminal.lifecycle")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt_without_terminal,completed",
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE_STATE_FILE" to lifecycleStatePath.toString(),
      ),
    )
    try {
      assertThat(client.listThreads(archived = false)).isEmpty()

      assertThat(withTimeoutOrNull(500.milliseconds) {
        client.suggestPrompt(createPromptSuggestionRequest())
      }).isNull()

      assertThat(client.suggestPrompt(createPromptSuggestionRequest())).isEqualTo(expectedGeneratedPromptSuggestionResult())

      val methods = Files.readAllLines(requestLogPath)
      assertThat(methods).contains("turn/interrupt")
      assertThat(methods.count { it == "initialize" }).isEqualTo(2)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptReturnsNullWhenTurnCompletesAsInterrupted(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-interrupted")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-interrupted-requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "interrupted",
      ),
    )
    try {
      assertThat(client.suggestPrompt(createPromptSuggestionRequest())).isNull()
      assertThat(Files.readAllLines(requestLogPath)).doesNotContain("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun suggestPromptRealParentTimeoutDoesNotPoisonClient(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-real-timeout")
    Files.createDirectories(backendDir)
    createRealPromptSuggestionHarness(
      scope = this,
      tempDir = backendDir,
      responsePlans = listOf(
        MockResponsesPlan.inProgressAssistantMessage(renderGeneratedPromptSuggestionPayload()),
        MockResponsesPlan.completedAssistantMessage(renderGeneratedPromptSuggestionPayload()),
      ),
    ).use { harness ->
      assertThat(withTimeoutOrNull(1500.milliseconds) {
        harness.client.suggestPrompt(
          createPromptSuggestionRequest(
            cwd = harness.projectDir.toString(),
            model = "mock-model",
          )
        )
      }).isNull()

      assertThat(
        harness.client.suggestPrompt(
          createPromptSuggestionRequest(
            cwd = harness.projectDir.toString(),
            model = "mock-model",
          )
        )
      ).isEqualTo(expectedGeneratedPromptSuggestionResult())
      assertThat(harness.responsesServer.requests()).hasSize(2)
    }
  }

  @Test
  fun suggestPromptSurfacesFailedTurnErrors(): Unit = runBlocking(Dispatchers.Default) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-prompt-suggest-failed")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
      environmentOverrides = mapOf(
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "failed",
        "CODEX_TEST_PROMPT_SUGGEST_ERROR_MESSAGE" to "prompt turn failed",
      ),
    )
    try {
      try {
        client.suggestPrompt(createPromptSuggestionRequest())
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("prompt turn failed")
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsUsesPathOverrideWhenExecutableProviderMissing(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-path-override")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-path",
          title = "Path Thread",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )

    val backendDir = tempDir.resolve("backend-path-override")
    Files.createDirectories(backendDir)
    val codexShim = createMockCodexShim(backendDir, configPath)
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { null },
      environmentOverrides = mapOf("PATH" to codexShim.parent.toString()),
      workingDirectory = workingDir,
    )
    try {
      val threads = client.listThreads(archived = false)
      assertThat(threads.map { it.id }).containsExactly("thread-path")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsFailsWithoutFallbackWhenConfiguredExecutableIsInvalid(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-invalid-exec")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-invalid-exec")
    Files.createDirectories(backendDir)
    val codexShim = createMockCodexShim(backendDir, configPath)
    val invalidExecutable = tempDir.resolve("missing-codex").toString()
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { invalidExecutable },
      environmentOverrides = mapOf("PATH" to codexShim.parent.toString()),
      workingDirectory = workingDir,
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains(invalidExecutable)
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsReportsDefaultExecutableStartFailuresWithoutCliMissingError(): Unit = runBlocking(Dispatchers.Default) {
    val workingDir = tempDir.resolve("project-invalid-env")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { null },
      environmentOverrides = mapOf("INVALID=KEY" to "value"),
      workingDirectory = workingDir,
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e).isNotInstanceOf(CodexCliNotFoundException::class.java)
        assertThat(e.message).contains("Failed to start Codex app-server from codex")
      }
    }
    finally {
      client.shutdown()
    }
  }

  private fun createPromptSuggestionRequest(
    cwd: String = "/work/project",
    model: String = "gpt-5.4",
  ): CodexPromptSuggestionRequest {
    return CodexPromptSuggestionRequest(
      cwd = cwd,
      targetMode = "new_task",
      model = model,
      reasoningEffort = "low",
      maxCandidates = 3,
      contextItems = listOf(
        CodexPromptSuggestionContextItem(
          rendererId = "testFailures",
          title = "Failing tests",
          body = "failed: ParserTest",
          payload = CodexAppServerValue.Obj(
            linkedMapOf(
              "statusCounts" to CodexAppServerValue.Obj(
                linkedMapOf(
                  "failed" to CodexAppServerValue.Num("2"),
                )
              ),
              "focus" to CodexAppServerValue.Str("tests"),
            )
          ),
          itemId = "failure-1",
          parentItemId = "suite-1",
          source = "testRunner",
          truncation = CodexPromptSuggestionContextTruncation(
            originalChars = 1200,
            includedChars = 480,
            reason = "source_limit",
          ),
        )
      ),
      seedCandidates = listOf(
        CodexPromptSuggestionCandidate(
          id = "tests.fix",
          label = "Fix failing tests",
          promptText = "Investigate the failing tests and implement the minimal fix.",
        ),
        CodexPromptSuggestionCandidate(
          id = "tests.explain",
          label = "Explain failures",
          promptText = "Explain why the selected tests are failing.",
        ),
        CodexPromptSuggestionCandidate(
          id = "tests.stabilize",
          label = "Stabilize test coverage",
          promptText = "Stabilize the selected tests and call out any missing cleanup.",
        ),
      ),
    )
  }

  private fun expectedGeneratedPromptSuggestionResult(): CodexPromptSuggestionResult.GeneratedCandidates {
    return CodexPromptSuggestionResult.GeneratedCandidates(
      listOf(
        CodexPromptSuggestionCandidate(
          label = "AI: Investigate provided context",
          promptText = "Investigate the provided context and explain the next steps.",
        ),
        CodexPromptSuggestionCandidate(
          label = "AI: Summarize provided context",
          promptText = "Summarize the relevant context before making changes.",
        ),
      )
    )
  }

  private fun renderGeneratedPromptSuggestionPayload(): String {
    return """
      {"kind":"generatedCandidates","candidates":[
        {"id":null,"label":"AI: Investigate provided context","promptText":"Investigate the provided context and explain the next steps."},
        {"id":null,"label":"AI: Summarize provided context","promptText":"Summarize the relevant context before making changes."}
      ]}
    """.trimIndent()
  }
}
