// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeThreadRenameEngineTest {
  @Test
  fun archiveThreadRunsResumeRenameCommandAndWaitsForArchivedState() {
    runBlocking(Dispatchers.Default) {
      var threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      val invocations = mutableListOf<CommandInvocation>()
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            invocations.add(CommandInvocation(path, launchSpec.command, launchSpec.envVariables))
            threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", archived = true, updatedAt = 101L))
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      val invocation = invocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.launchCommand)
        .containsExactly(
          "claude",
          "--resume",
          "session-1",
          "--permission-mode",
          "default",
          "--print",
          "--name",
          "[archived] Thread title",
          "--",
          "Acknowledge the session title update only. Do not inspect or modify files.",
        )
      assertThat(invocation.environment).containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
    }
  }

  @Test
  fun unarchiveThreadRunsResumeRenameCommandAndWaitsForVisibleState() {
    runBlocking(Dispatchers.Default) {
      var threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", archived = true, updatedAt = 100L))
      var recordedCommand: List<String>? = null
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            recordedCommand = launchSpec.command
            threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", archived = false, updatedAt = 101L))
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.unarchiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      assertThat(recordedCommand)
        .containsExactly(
          "claude",
          "--resume",
          "session-1",
          "--permission-mode",
          "default",
          "--print",
          "--name",
          "Thread title",
          "--",
          "Acknowledge the session title update only. Do not inspect or modify files.",
        )
    }
  }

  @Test
  fun renameThreadRunsResumeRenameCommandAndWaitsForNewTitle() {
    runBlocking(Dispatchers.Default) {
      var threads = listOf(ClaudeBackendThread(id = "session-1", title = "Old title", updatedAt = 100L))
      val invocations = mutableListOf<CommandInvocation>()
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            invocations.add(CommandInvocation(path, launchSpec.command, launchSpec.envVariables))
            threads = listOf(ClaudeBackendThread(id = "session-1", title = "New title", updatedAt = 101L))
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.rename(path = PROJECT_PATH, threadId = "session-1", newTitle = "New title")).isTrue()
      val invocation = invocations.single()
      assertThat(invocation.launchCommand)
        .containsExactly(
          "claude",
          "--resume",
          "session-1",
          "--permission-mode",
          "default",
          "--print",
          "--name",
          "New title",
          "--",
          "Acknowledge the session title update only. Do not inspect or modify files.",
        )
    }
  }

  @Test
  fun renameThreadIsNoOpWhenTitleMatches() {
    runBlocking(Dispatchers.Default) {
      val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Same title", updatedAt = 100L))
      val invocations = mutableListOf<List<String>>()
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            invocations.add(launchSpec.command)
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
      )

      assertThat(engine.rename(path = PROJECT_PATH, threadId = "session-1", newTitle = "Same title")).isTrue()
      assertThat(invocations).isEmpty()
    }
  }

  @Test
  fun archiveThreadDispatchesThroughOpenTabAndSkipsFallbackRunner() {
    runBlocking(Dispatchers.Default) {
      var threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      var fallbackRenameTitle: String? = null
      var dispatchedCommand: String? = null
      val engine = ClaudeOpenTabAwareThreadRenameEngine(
        backend = testBackend { threads },
        fallbackEngine = fallbackRenameEngine { _, _, newTitle ->
          fallbackRenameTitle = newTitle
          true
        },
        openTabDispatcher = ClaudeOpenTabRenameDispatcher { _, _, _, dispatchPlan ->
          dispatchedCommand = dispatchPlan.postStartDispatchSteps.single().text
          threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", archived = true, updatedAt = 101L))
          true
        },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      assertThat(dispatchedCommand).isEqualTo("/rename [archived] Thread title")
      assertThat(fallbackRenameTitle).isNull()
    }
  }

  @Test
  fun archiveThreadFallsBackToPtyRunnerWhenNoOpenTabExists() {
    runBlocking(Dispatchers.Default) {
      val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      var fallbackRenameTitle: String? = null
      val engine = ClaudeOpenTabAwareThreadRenameEngine(
        backend = testBackend { threads },
        fallbackEngine = fallbackRenameEngine { _, _, newTitle ->
          fallbackRenameTitle = newTitle
          true
        },
        openTabDispatcher = ClaudeOpenTabRenameDispatcher { _, _, _, _ -> false },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      assertThat(fallbackRenameTitle).isEqualTo("[archived] Thread title")
    }
  }

  @Test
  fun archiveThreadReturnsTrueWhenOpenTabDispatchSucceedsEvenIfObservedStateLags() {
    runBlocking(Dispatchers.Default) {
      var currentTime = 0L
      val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      var fallbackInvoked = false
      val engine = ClaudeOpenTabAwareThreadRenameEngine(
        backend = testBackend { threads },
        fallbackEngine = fallbackRenameEngine { _, _, _ ->
          fallbackInvoked = true
          true
        },
        openTabDispatcher = ClaudeOpenTabRenameDispatcher { _, _, _, _ -> true },
        waitTimeoutMs = 3L,
        pollIntervalMs = 1L,
        currentTimeMs = { currentTime },
        delayFn = { duration ->
          currentTime += duration.inWholeMilliseconds
        },
      )

      // Once the open-tab dispatch succeeded, trust the `/rename` delivery and let the
      // follow-up provider refresh reconcile the tab title asynchronously instead of
      // surfacing a false failure to the user.
      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      assertThat(fallbackInvoked).isFalse()
    }
  }

  @Test
  fun archiveThreadReturnsFalseWhenRenameRunnerFails() {
    runBlocking(Dispatchers.Default) {
      val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            return ClaudeThreadCommandResult(
              successful = false,
              outputTail = "Unknown option: --name",
              failureReason = "Unknown option: --name",
            )
          }
        },
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isFalse()
    }
  }

  @Test
  fun archiveThreadReturnsFalseWhenObservedStateNeverChanges() {
    runBlocking(Dispatchers.Default) {
      var currentTime = 0L
      val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
        waitTimeoutMs = 3L,
        pollIntervalMs = 1L,
        currentTimeMs = { currentTime },
        delayFn = { duration ->
          currentTime += duration.inWholeMilliseconds
        },
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isFalse()
    }
  }

  @Test
  fun archiveAndUnarchiveCommandsPreserveLongTitle() {
    runBlocking(Dispatchers.Default) {
      val longTitle = "Claude thread title " + "x".repeat(160)
      var threads = listOf(ClaudeBackendThread(id = "session-1", title = longTitle, updatedAt = 100L))
      val recordedCommands = mutableListOf<List<String>>()
      val engine = PtyClaudeThreadRenameEngine(
        backend = testBackend { threads },
        commandRunner = object : ClaudeThreadCommandRunner {
          override suspend fun run(
            path: String,
            launchSpec: AgentSessionTerminalLaunchSpec,
            timeoutMs: Int,
          ): ClaudeThreadCommandResult {
            recordedCommands.add(launchSpec.command)
            threads = if (recordedCommands.size == 1) {
              listOf(ClaudeBackendThread(id = "session-1", title = longTitle, archived = true, updatedAt = 101L))
            }
            else {
              listOf(ClaudeBackendThread(id = "session-1", title = longTitle, archived = false, updatedAt = 102L))
            }
            return ClaudeThreadCommandResult(successful = true, outputTail = "")
          }
        },
        waitTimeoutMs = 1_000L,
        pollIntervalMs = 1L,
        delayFn = {},
      )

      assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()
      assertThat(engine.unarchiveThread(path = PROJECT_PATH, threadId = "session-1")).isTrue()

      assertThat(recordedCommands)
        .containsExactly(
          listOf(
            "claude",
            "--resume",
            "session-1",
            "--permission-mode",
            "default",
            "--print",
            "--name",
            "[archived] $longTitle",
            "--",
            "Acknowledge the session title update only. Do not inspect or modify files.",
          ),
          listOf(
            "claude",
            "--resume",
            "session-1",
            "--permission-mode",
            "default",
            "--print",
            "--name",
            longTitle,
            "--",
            "Acknowledge the session title update only. Do not inspect or modify files.",
          ),
        )
    }
  }

  @Test
  fun archiveThreadLogsCommandOutputTailOnObservedStateTimeout() {
    val warns = mutableListOf<String>()
    val diagnosticTail = "diagnostic-output-12345"
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warns.add(message)
        return false
      }
    }) {
      runBlocking(Dispatchers.Default) {
        var currentTime = 0L
        val threads = listOf(ClaudeBackendThread(id = "session-1", title = "Thread title", updatedAt = 100L))
        val engine = PtyClaudeThreadRenameEngine(
          backend = testBackend { threads },
          commandRunner = object : ClaudeThreadCommandRunner {
            override suspend fun run(
              path: String,
              launchSpec: AgentSessionTerminalLaunchSpec,
              timeoutMs: Int,
            ): ClaudeThreadCommandResult {
              return ClaudeThreadCommandResult(successful = true, outputTail = diagnosticTail)
            }
          },
          waitTimeoutMs = 3L,
          pollIntervalMs = 1L,
          currentTimeMs = { currentTime },
          delayFn = { duration ->
            currentTime += duration.inWholeMilliseconds
          },
        )

        assertThat(engine.archiveThread(path = PROJECT_PATH, threadId = "session-1")).isFalse()
      }
    }
    assertThat(warns).anyMatch { it.contains(diagnosticTail) }
  }
}

private data class CommandInvocation(
  val path: String,
  val launchCommand: List<String>,
  val environment: Map<String, String>,
)

private fun fallbackRenameEngine(rename: suspend (path: String, threadId: String, newTitle: String) -> Boolean): ClaudeThreadRenameEngine {
  return object : ClaudeThreadRenameEngine {
    override suspend fun rename(path: String, threadId: String, newTitle: String): Boolean {
      return rename(path, threadId, newTitle)
    }

    override suspend fun archiveThread(path: String, threadId: String): Boolean {
      error("archiveThread should not be invoked in fallback test stub")
    }

    override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
      error("unarchiveThread should not be invoked in fallback test stub")
    }
  }
}

private const val PROJECT_PATH = "/tmp/project"

private fun testBackend(provider: () -> List<ClaudeBackendThread>): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = provider()
  }
}
