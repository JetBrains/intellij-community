// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeHookBridgeTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun createLaunchSettingsWritesClaudeHookSettings() {
    val sessionId = "session-settings"
    val settings = checkNotNull(createLaunchSettings(sessionId))

    try {
      assertThat(settings.endpoint).isEqualTo("http://localhost:4321/$CLAUDE_HOOK_ENDPOINT_PREFIX")
      assertThat(settings.token).isNotBlank()
      val settingsText = Files.readString(Path.of(settings.settingsPath))
      assertThat(settingsText).contains("\"PreToolUse\"")
      assertThat(settingsText).contains("\"PostToolUse\"")
      assertThat(settingsText).contains("AskUserQuestion|ExitPlanMode")
      assertThat(settingsText).contains("Write|Edit|MultiEdit|NotebookEdit")
      assertThat(settingsText).contains("\"type\":\"http\"")
      assertThat(settingsText).contains("\"url\":\"${settings.endpoint}\"")
      assertThat(settingsText).contains("\"Authorization\":\"Bearer ${settings.token}\"")
      assertThat(settingsText).contains("\"timeout\":1")
      assertThat(settingsText).contains(settings.endpoint)
      assertThat(settingsText).contains(settings.token)
      assertThat(settingsText).doesNotContain("python3")
      assertThat(settingsText).doesNotContain("\"command\"")
      assertThat(Files.exists(tempDir.resolve("hook-settings/claude-hook-forwarder.py"))).isFalse()
    }
    finally {
      ClaudeHookBridge.invalidateSession(sessionId)
    }
  }

  @Test
  fun invalidateSessionDeletesGeneratedHookSettings() {
    val sessionId = "session-settings-cleanup"
    val settings = checkNotNull(createLaunchSettings(sessionId))
    val settingsPath = Path.of(settings.settingsPath)
    assertThat(Files.exists(settingsPath)).isTrue()

    ClaudeHookBridge.invalidateSession(sessionId)

    assertThat(Files.exists(settingsPath)).isFalse()
  }

  @Test
  fun preToolUseUserQuestionEmitsNeedsInputHint() {
    runBlocking(Dispatchers.Default) {
      val sessionId = "session-question-hook"
      val projectPath = tempDir.resolve("question-project")
      val settings = checkNotNull(createLaunchSettings(sessionId))
      val update = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeout(5.seconds) {
          ClaudeHookBridge.updateEvents.first { event -> sessionId in event.activityUpdatesByThreadId }
        }
      }

      try {
        val result = ClaudeHookBridge.handleHookRequest(
          token = settings.token,
          content = hookPayload(
            sessionId = sessionId,
            cwd = projectPath.toString(),
            hookEventName = "PreToolUse",
            toolName = "AskUserQuestion",
          ),
        )

        assertThat(result).isEqualTo(ClaudeHookRequestResult.ACCEPTED)
        val event = update.await()
        assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event.scopedPaths).containsExactly(normalizeAgentWorkbenchPath(projectPath.toString()))
        assertThat(event.threadIds).isNull()
        assertThat(event.activityUpdatesByThreadId.getValue(sessionId).activityReport)
          .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.NEEDS_INPUT))
        assertThat(event.mayHaveChangedProjectFiles).isFalse()
      }
      finally {
        ClaudeHookBridge.invalidateSession(sessionId)
      }
    }
  }

  @Test
  fun postToolUseEditEmitsExactProjectFileRefreshHint() {
    runBlocking(Dispatchers.Default) {
      val sessionId = "session-edit-hook"
      val projectPath = tempDir.resolve("edit-project")
      val changedPath = projectPath.resolve("src/Main.kt")
      val settings = checkNotNull(createLaunchSettings(sessionId))
      val expectedChangedPath = normalizeAgentWorkbenchPath(changedPath.toString())
      val update = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeout(5.seconds) {
          ClaudeHookBridge.updateEvents.first { event ->
            event.changedProjectFilePaths?.contains(expectedChangedPath) == true
          }
        }
      }

      try {
        val result = ClaudeHookBridge.handleHookRequest(
          token = settings.token,
          content = hookPayload(
            sessionId = sessionId,
            cwd = projectPath.toString(),
            hookEventName = "PostToolUse",
            toolName = "Edit",
            toolInputJson = """{"file_path":"src/Main.kt","old_string":"old","new_string":"new"}""",
          ),
        )

        assertThat(result).isEqualTo(ClaudeHookRequestResult.ACCEPTED)
        val event = update.await()
        assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event.scopedPaths).containsExactly(normalizeAgentWorkbenchPath(projectPath.toString()))
        assertThat(event.threadIds).containsExactly(sessionId)
        assertThat(event.mayHaveChangedProjectFiles).isTrue()
        assertThat(event.changedProjectFilePaths).containsExactly(expectedChangedPath)
      }
      finally {
        ClaudeHookBridge.invalidateSession(sessionId)
      }
    }
  }

  @Test
  fun wrongTokenIsRejectedWithoutUpdate() {
    runBlocking(Dispatchers.Default) {
      val sessionId = "session-wrong-token"
      val projectPath = tempDir.resolve("wrong-token-project")
      val settings = checkNotNull(createLaunchSettings(sessionId))

      try {
        val result = assertNoHookUpdateFor(sessionId) {
          ClaudeHookBridge.handleHookRequest(
            token = "wrong-token",
            content = hookPayload(
              sessionId = sessionId,
              cwd = projectPath.toString(),
              hookEventName = "PreToolUse",
              toolName = "AskUserQuestion",
            ),
          )
        }

        assertThat(result).isEqualTo(ClaudeHookRequestResult.UNAUTHORIZED)
        assertThat(settings.token).isNotEqualTo("wrong-token")
      }
      finally {
        ClaudeHookBridge.invalidateSession(sessionId)
      }
    }
  }

  private fun createLaunchSettings(sessionId: String): ClaudeHookLaunchSettings? {
    return ClaudeHookBridge.createLaunchSettings(
      sessionId = sessionId,
      portProvider = { 4321 },
      settingsDirectoryProvider = { tempDir.resolve("hook-settings") },
    )
  }
}

private suspend fun assertNoHookUpdateFor(
  sessionId: String,
  action: () -> ClaudeHookRequestResult,
): ClaudeHookRequestResult = coroutineScope {
  val update = async(start = CoroutineStart.UNDISPATCHED) {
    withTimeout(200.milliseconds) {
      ClaudeHookBridge.updateEvents.first { event ->
        sessionId in event.activityUpdatesByThreadId || event.threadIds?.contains(sessionId) == true
      }
    }
  }
  val result = action()
  assertThat(runCatching { update.await() }.exceptionOrNull()).isInstanceOf(TimeoutCancellationException::class.java)
  result
}

private fun hookPayload(
  sessionId: String,
  cwd: String,
  hookEventName: String,
  toolName: String,
  toolInputJson: String = "{}",
): String {
  return """
    {
      "hook_event_name":${hookEventName.jsonString()},
      "session_id":${sessionId.jsonString()},
      "cwd":${cwd.jsonString()},
      "tool_name":${toolName.jsonString()},
      "tool_input":$toolInputJson
    }
  """.trimIndent()
}

private fun String.jsonString(): String {
  return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
