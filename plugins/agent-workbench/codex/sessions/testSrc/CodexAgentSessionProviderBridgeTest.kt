// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class CodexAgentSessionProviderBridgeTest {
  private val bridge = CodexAgentSessionProviderBridge()

  @Test
  fun buildResumeLaunchSpec() {
    assertThat(bridge.buildResumeLaunchSpec("thread-1").command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
  }

  @Test
  fun buildNewEntryLaunchSpec() {
    assertThat(bridge.buildNewEntryLaunchSpec().command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false")
  }

  @Test
  fun buildNewSessionLaunchSpec() {
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false")
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO).command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "--full-auto")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptForYoloCommand() {
    assertThat(
      bridge.buildLaunchSpecWithInitialPrompt(
        baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO),
        "-draft plan\nstep 2",
      ).command
    )
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "--full-auto", "--", "-draft plan\nstep 2")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptForResumeCommand() {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("thread-1")

    assertThat(bridge.buildLaunchSpecWithInitialPrompt(resumeLaunchSpec, "Summarize changes").command)
      .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1", "--", "Summarize changes")
  }

  @Test
  fun supportsUnarchiveThread() {
    assertThat(bridge.supportsUnarchiveThread).isTrue()
  }

  @Test
  fun createNewSessionReturnsPendingLaunchSpec() {
    runBlocking(Dispatchers.Default) {
      val standard = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.STANDARD)
      assertThat(standard.sessionId).isNull()
      assertThat(standard.launchSpec.command).containsExactly("codex", "-c", "check_for_update_on_startup=false")

      val yolo = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.YOLO)
      assertThat(yolo.sessionId).isNull()
      assertThat(yolo.launchSpec.command).containsExactly("codex", "-c", "check_for_update_on_startup=false", "--full-auto")
    }
  }

  @Test
  fun composeInitialMessageWithoutContext() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "  Refactor this  ")
    )

    assertThat(plan.message).isEqualTo("Refactor this")
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
  }

  @Test
  fun composeInitialMessagePrefixesPlanCommandWhenEnabled() {
    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        codexPlanModeEnabled = true,
      )
    )

    assertThat(message).isEqualTo("/plan Refactor this")
  }

  @Test
  fun composeInitialMessageDoesNotDoublePrefixPlanCommand() {
    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = " /plan Refactor this ",
        codexPlanModeEnabled = true,
      )
    )

    assertThat(message).isEqualTo("/plan Refactor this")
  }

  @Test
  fun initialMessagePlanPoliciesDependOnPlanModeAndCommand() {
    val defaultPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "Refactor this")
    )
    assertThat(defaultPlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(defaultPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val planModePlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        codexPlanModeEnabled = true,
      )
    )
    assertThat(planModePlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(planModePlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)

    val plannerPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/planner follow-up")
    )
    assertThat(plannerPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val manualPlanCommand = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/plan from manual input")
    )
    assertThat(manualPlanCommand.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(manualPlanCommand.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Selection",
            body = "val answer = 42",
            source = "editor",
          )
        ),
        contextEnvelopeSummary = AgentPromptContextEnvelopeSummary(
          softCapChars = 12_000,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )
    )

    assertThat(message).startsWith("Refactor this\n\n### IDE Context")
    assertThat(message).contains("soft-cap: limit=12000 auto-trim=no")
    assertThat(message).contains("snippet")
    assertThat(message).doesNotContain("lang=")
    assertThat(message).contains("```\nval answer = 42\n```")
    assertThat(message).doesNotContain("```text")
    assertThat(message).contains("val answer = 42")
    assertThat(message).doesNotContain("Metadata:")
    assertThat(message).doesNotContain("####")
    assertThat(message).doesNotContain("Items:")
    assertThat(message).doesNotContain("<context_envelope>")
    assertThat(message).doesNotContain("<context_item>")
    assertThat(message).doesNotContain("\"schema\"")
  }

  @Test
  fun composeInitialMessageUsesSnippetLanguageWhenProvided() {
    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Selection",
            body = "val answer = 42",
            payload = AgentPromptPayload.obj(
              "language" to AgentPromptPayload.str("JAVA"),
            ),
            source = "editor",
          )
        ),
      )
    )

    assertThat(message).doesNotContain("lang=")
    assertThat(message).contains("```java\nval answer = 42\n```")
  }

  @Test
  fun composeInitialMessageOmitsSnippetLanguageForInvalidValue() {
    val invalidLanguage = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Selection",
            body = "val answer = 42",
            payload = AgentPromptPayload.obj(
              "language" to AgentPromptPayload.str("java script!"),
            ),
            source = "editor",
          )
        ),
      )
    )

    assertThat(invalidLanguage).doesNotContain("lang=")
    assertThat(invalidLanguage).contains("```\nval answer = 42\n```")
    assertThat(invalidLanguage).doesNotContain("```java")
  }

  @Test
  fun composeInitialMessageResolvesRelativePathsAgainstProjectRoot() {
    val projectRoot = Path.of("/work/project")
    val expectedFile = projectRoot.resolve("src/Main.java").normalize().toString()
    val expectedPathFile = projectRoot.resolve("src/App.kt").normalize().toString()
    val expectedPathDir = projectRoot.resolve("src").normalize().toString()

    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Review context",
        projectPath = projectRoot.toString(),
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.FILE,
            title = "Current File",
            body = "src/Main.java",
            payload = AgentPromptPayload.obj(
              "path" to AgentPromptPayload.str("src/Main.java"),
            ),
            source = "editor",
          ),
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Selection",
            body = "file: src/App.kt\ndir: src",
            payload = AgentPromptPayload.obj(
              "entries" to AgentPromptPayload.arr(
                AgentPromptPayload.obj(
                  "kind" to AgentPromptPayload.str("file"),
                  "path" to AgentPromptPayload.str("src/App.kt"),
                ),
                AgentPromptPayload.obj(
                  "kind" to AgentPromptPayload.str("dir"),
                  "path" to AgentPromptPayload.str("src"),
                ),
              ),
            ),
            source = "projectView",
          ),
        ),
      )
    )

    assertThat(message).contains("file: $expectedFile")
    assertThat(message).contains("paths:")
    assertThat(message).contains(expectedPathFile)
    assertThat(message).contains(expectedPathDir)
  }

  @Test
  fun composeInitialMessageMarksUnresolvedRelativePathWithoutProjectRoot() {
    val message = messageFor(bridge,
      AgentPromptInitialMessageRequest(
        prompt = "Review context",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.FILE,
            title = "Current File",
            body = "src/Main.java",
            payload = AgentPromptPayload.obj(
              "path" to AgentPromptPayload.str("src/Main.java"),
            ),
            source = "editor",
          )
        ),
      )
    )

    assertThat(message).contains("file: src/Main.java [path-unresolved]")
  }

}

private fun messageFor(bridge: CodexAgentSessionProviderBridge, request: AgentPromptInitialMessageRequest): String {
  return checkNotNull(bridge.buildInitialMessagePlan(request).message)
}
