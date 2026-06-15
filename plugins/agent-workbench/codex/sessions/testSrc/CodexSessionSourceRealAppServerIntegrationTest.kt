// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexWebSocketAppServerClient
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceRealAppServerIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun realAppServerForkRenameKeepsOriginalAndForkIdentities() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      val tempRoot = tempDir.resolve("fork-rename")
      val projectDir = testCreateProjectDir(tempRoot, "project").toRealPath()
      val codexHome = tempRoot.resolve("codex-home")
      Files.createDirectories(codexHome)

      MockResponsesServer(listOf(MockResponsesPlan.completedAssistantMessage("Original ready"))).use { server ->
        writeCodexConfig(
          configPath = codexHome.resolve("config.toml"),
          projectDir = projectDir,
          responsesBaseUrl = server.baseUri + "/v1",
        )
        val client = CodexWebSocketAppServerClient(
          coroutineScope = this,
          executablePathProvider = { codexBinary },
          environmentOverrides = mapOf("CODEX_HOME" to codexHome.toString()),
          workingDirectory = projectDir,
        )
        val notifications = MutableSharedFlow<CodexAppServerNotification>(extraBufferCapacity = 64)
        val seenNotifications = CopyOnWriteArrayList<CodexAppServerNotification>()
        val notificationBridge = launch {
          client.notifications.collect { notification ->
            seenNotifications.add(notification)
            notifications.emit(notification)
          }
        }
        try {
          val originalSession = client.createThreadSession(cwd = projectDir.toString())
          val originalId = originalSession.thread.id
          client.persistThread(originalId, text = "Create the original thread for fork rename testing.")

          val source = createRealAppServerSource(client = client, notifications = notifications)
          val originalRows = eventually(timeout = 30.seconds) {
            source.listThreadsFromClosedProject(projectDir.toString())
              .takeIf { rows -> rows.any { it.id == originalId } }
          } ?: error("Timed out waiting for original thread $originalId to appear in Codex app-server thread/list")
          val originalTitle = originalRows.single { it.id == originalId }.title
          val normalizedProjectPath = normalizeRootPath(projectDir.invariantSeparatorsPathString)

          val startedUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(10.seconds) {
              source.updateEvents.first { event ->
                event.type == AgentSessionSourceUpdate.THREADS_CHANGED && event.scopedPaths != null
              }
            }
          }
          val forkSession = client.forkThread(originalId)
          val forkId = forkSession.thread.id
          val forkScopedPath = forkSession.thread.cwd?.let(::normalizeRootPath) ?: normalizedProjectPath
          assertThat(forkId).isNotEqualTo(originalId)
          val startedEvent = startedUpdate.await()
          assertThat(startedEvent)
            .withFailMessage("Observed Codex app-server notifications: %s", seenNotifications)
            .isNotNull
          assertThat(startedEvent!!.threadIds).isNull()
          assertThat(startedEvent.scopedPaths).containsExactly(forkScopedPath)

          val afterForkRows = eventually(timeout = 30.seconds) {
            val rows = source.listThreadsFromClosedProject(projectDir.toString())
            rows.takeIf { it.any { row -> row.id == originalId } && it.any { row -> row.id == forkId } }
          } ?: error("Timed out waiting for fork $forkId and original $originalId in Codex app-server thread/list")
          assertThat(afterForkRows.map { it.id }).contains(originalId, forkId)

          val renameUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10.seconds) {
              source.updateEvents.first { event -> event.threadIds?.contains(forkId) == true }
            }
          }
          client.setThreadName(threadId = forkId, name = "Renamed fork")
          val renameEvent = renameUpdate.await()
          assertThat(renameEvent.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
          assertThat(renameEvent.scopedPaths).contains(forkScopedPath)
          assertThat(renameEvent.threadIds).containsExactly(forkId)

          val renamedRows = eventually(timeout = 30.seconds) {
            val rows = source.listThreadsFromClosedProject(projectDir.toString())
            val original = rows.firstOrNull { it.id == originalId }
            val fork = rows.firstOrNull { it.id == forkId }
            rows.takeIf { original != null && fork?.title == "Renamed fork" }
          } ?: error("Timed out waiting for renamed fork $forkId in Codex app-server thread/list")

          val originalAfterRename = renamedRows.single { it.id == originalId }
          val forkAfterRename = renamedRows.single { it.id == forkId }
          assertThat(originalAfterRename.provider).isEqualTo(AgentSessionProvider.CODEX)
          assertThat(forkAfterRename.provider).isEqualTo(AgentSessionProvider.CODEX)
          assertThat(originalAfterRename.title).isEqualTo(originalTitle)
          assertThat(forkAfterRename.title).isEqualTo("Renamed fork")
        }
        finally {
          notificationBridge.cancelAndJoin()
          client.shutdown()
        }
      }
    }
  }

  private fun createRealAppServerSource(
    client: CodexWebSocketAppServerClient,
    notifications: Flow<CodexAppServerNotification>,
  ): CodexSessionSource {
    val backend = CodexAppServerSessionBackend(
      listThreadsForProject = { projectPath ->
        client.listThreads(
          archived = false,
          cwdFilter = normalizeRootPath(projectPath.invariantSeparatorsPathString),
        )
      },
      readThread = client::readThread,
      archiveThread = {},
    )
    val appServerRefreshHintsProvider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = client::readThreadActivitySnapshot,
      notifications = notifications,
    )
    return CodexSessionSource(
      backend,
      appServerRefreshHintsProvider,
      EmptyCodexRefreshHintsProvider,
    )
  }

  private fun writeCodexConfig(configPath: Path, projectDir: Path, responsesBaseUrl: String) {
    Files.writeString(
      configPath,
      buildString {
        appendLine("model = \"mock-model\"")
        appendLine("model_provider = \"mock_provider\"")
        appendLine("approval_policy = \"never\"")
        appendLine("sandbox_mode = \"read-only\"")
        appendLine("suppress_unstable_features_warning = true")
        appendLine()
        appendLine("[projects]")
        appendLine("${tomlString(projectDir.toString())} = { trust_level = \"trusted\" }")
        appendLine()
        appendLine("[model_providers.mock_provider]")
        appendLine("name = \"Mock provider for test\"")
        appendLine("base_url = ${tomlString(responsesBaseUrl)}")
        appendLine("wire_api = \"responses\"")
        appendLine("request_max_retries = 0")
        appendLine("stream_max_retries = 0")
      }
    )
  }

  private fun tomlString(value: String): String {
    return buildString {
      append('"')
      value.forEach { char ->
        when (char) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          else -> append(char)
        }
      }
      append('"')
    }
  }

  private fun requireRealCodexBinary(): String {
    assumeTrue(CodexRealTuiHarness.isSupportedPlatform(), "Real Codex app-server test is supported on macOS/Linux only.")
    val codexBinary = CodexRealTuiHarness.resolveCodexBinary()
    assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")
    return codexBinary!!
  }
}

private object EmptyCodexRefreshHintsProvider : CodexRefreshHintsProvider {
  override val updateEvents = emptyFlow<AgentSessionSourceUpdateEvent>()

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> = emptyMap()
}
