// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class CodexSessionSourceRealTuiIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun completedRealTuiTurnUnreadHintClearsAfterReadTracking() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("ready"),
        responsePlans = listOf(MockResponsesPlan.completedAssistantMessage("Done")),
      ).use { harness ->
        harness.start(prompt = "Reply once and stop.").use { session ->
          val threadId = session.awaitThreadId()
          val source = testCreateSource(
            projectDir = harness.projectDir,
            codexHome = harness.codexHome,
            threadIds = listOf(threadId),
          )

          val unreadHint = eventually(timeout = 30.seconds) {
            testRefreshCodexHints(
              projectDir = harness.projectDir,
              codexHome = harness.codexHome,
              threadIds = listOf(threadId),
            ).activityHintsByThreadId[threadId]
              ?.takeIf { it.activity == AgentThreadActivity.UNREAD && !it.responseRequired }
          }

          assertThat(unreadHint)
            .withFailMessage("Timed out waiting for passive UNREAD rollout hint from real Codex TUI.\n%s", session.diagnostics())
            .isNotNull

          source.markThreadAsRead(threadId = threadId, updatedAt = unreadHint!!.updatedAt)

          val hintsAfterRead = eventually(timeout = 30.seconds) {
            testRefreshHints(source, harness.projectDir, listOf(threadId))
              .takeIf { it.activityByThreadId.isEmpty() }
          }

          assertThat(hintsAfterRead)
            .withFailMessage("Timed out waiting for read tracking to suppress passive unread rollout hint from real Codex TUI.\n%s", session.diagnostics())
            .isNotNull
        }
      }
    }
  }

  @Test
  fun inProgressRealTuiTurnKeepsProcessingWhenAppServerIsReady() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("processing"),
        responsePlans = listOf(MockResponsesPlan.inProgressAssistantMessage("Still working")),
      ).use { harness ->
        harness.start(prompt = "Start working and keep going.").use { session ->
          val threadId = session.awaitThreadId()
          val source = testCreateSource(
            projectDir = harness.projectDir,
            codexHome = harness.codexHome,
            threadIds = listOf(threadId),
            appServerHints = mapOf(
              harness.projectDir.toString() to testRefreshHintsOf(
                threadId to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
              )
            ),
          )

          val hint = eventually(timeout = 30.seconds) {
            testRefreshHints(source, harness.projectDir, listOf(threadId)).activityByThreadId[threadId]
              ?.takeIf { it == AgentThreadActivity.PROCESSING }
          }

          assertThat(hint)
            .withFailMessage("Timed out waiting for PROCESSING rollout hint from real Codex TUI.\n%s", session.diagnostics())
            .isNotNull
        }
      }
    }
  }

  @Test
  fun realTuiRequestUserInputProducesResponseRequiredUnread() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("request-user-input"),
        responsePlans = listOf(MockResponsesPlan.requestUserInput()),
        enableDefaultModeRequestUserInput = true,
      ).use { harness ->
        harness.start(prompt = "Ask a yes or no question.").use { session ->
          val threadId = session.awaitThreadId()
          val source = testCreateSource(
            projectDir = harness.projectDir,
            codexHome = harness.codexHome,
            threadIds = listOf(threadId),
            appServerHints = mapOf(
              harness.projectDir.toString() to testRefreshHintsOf(
                threadId to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
              )
            ),
          )

          val activity = eventually(timeout = 30.seconds) {
            testRefreshHints(source, harness.projectDir, listOf(threadId)).activityByThreadId[threadId]
              ?.takeIf { it == AgentThreadActivity.UNREAD }
          }
          val mergedHint = eventually(timeout = 30.seconds) {
            testRefreshCodexHints(
              projectDir = harness.projectDir,
              codexHome = harness.codexHome,
              threadIds = listOf(threadId),
              appServerHints = mapOf(
                harness.projectDir.toString() to testRefreshHintsOf(
                  threadId to testRefreshHint(activity = AgentThreadActivity.READY, updatedAt = 100L)
                )
              ),
            ).activityHintsByThreadId[threadId]?.takeIf { it.activity == AgentThreadActivity.UNREAD && it.responseRequired }
          }

          assertThat(activity)
            .withFailMessage("Timed out waiting for UNREAD source hint from real Codex TUI.\n%s", session.diagnostics())
            .isNotNull
          assertThat(mergedHint)
            .withFailMessage("Timed out waiting for response-required UNREAD Codex hint from real Codex TUI.\n%s", session.diagnostics())
            .isNotNull
        }
      }
    }
  }

  private fun requireRealCodexBinary(): String {
    assumeTrue(CodexRealTuiHarness.isSupportedPlatform(), "Real Codex TUI rollout test is supported on macOS/Linux only.")
    val codexBinary = CodexRealTuiHarness.resolveCodexBinary()
    assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")
    return codexBinary!!
  }
}
