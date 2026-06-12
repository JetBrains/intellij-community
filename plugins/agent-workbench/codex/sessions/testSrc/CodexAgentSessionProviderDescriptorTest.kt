// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.nio.file.Path

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexAgentSessionProviderDescriptorTest {
  private val bridge = CodexAgentSessionProviderDescriptor(
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
  )

  @Test
  fun buildResumeLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(bridge.buildResumeLaunchSpec("thread-1").command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("resume", "thread-1"))
  }

  @Test
  fun buildYoloResumeLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(bridge.buildResumeLaunchSpec("thread-1", AgentSessionLaunchMode.YOLO).command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--yolo", "resume", "thread-1"))
  }

  @Test
  fun promptOptionsUseSharedPlanModeOption() {
    assertThat(bridge.promptOptions).containsExactly(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)
    assertThat(bridge.supportsGenerationModelSelection).isTrue()
  }

  @Test
  fun buildNewSessionLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO).command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + "--yolo")
  }

  @Test
  fun applyGenerationSettingsLeavesAutoLaunchSpecUnchanged(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(bridge.applyGenerationSettings(baseLaunchSpec, AgentPromptGenerationSettings.AUTO, STANDARD_INITIAL_MESSAGE_PLAN).command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
  }

  @Test
  fun applyGenerationSettingsAddsReasoningEffortConfig(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH),
        STANDARD_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("-c", "model_reasoning_effort=\"xhigh\""))
  }

  @Test
  fun applyGenerationSettingsAddsModelFlag(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(modelId = "gpt-5.1-codex"),
        STANDARD_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--model", "gpt-5.1-codex"))
  }

  @Test
  fun applyGenerationSettingsAddsModelAndReasoningEffort(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
        STANDARD_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(
        CODEX_BASE_COMMAND + listOf("--model", "gpt-5.1-codex", "-c", "model_reasoning_effort=\"high\"")
      )
  }

  @Test
  fun applyGenerationSettingsAddsPlanModeReasoningEffortConfig(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
        PLAN_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(
        CODEX_BASE_COMMAND + listOf(
          "--model",
          "gpt-5.1-codex",
          "-c",
          "model_reasoning_effort=\"high\"",
          "-c",
          "plan_mode_reasoning_effort=\"high\"",
        )
      )
  }

  @Test
  fun applyGenerationSettingsKeepsPlanModeAutoReasoningAsProviderDefault(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(modelId = "gpt-5.1-codex"),
        PLAN_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--model", "gpt-5.1-codex"))
  }

  @Test
  fun applyGenerationSettingsInsertsPlanModeArgsBeforePromptSeparator(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).copy(
      command = CODEX_BASE_COMMAND + listOf("--", "Refactor this"),
    )

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
        PLAN_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(
        CODEX_BASE_COMMAND + listOf(
          "--model",
          "gpt-5.1-codex",
          "-c",
          "model_reasoning_effort=\"high\"",
          "-c",
          "plan_mode_reasoning_effort=\"high\"",
          "--",
          "Refactor this",
        )
      )
  }

  @Test
  fun applyGenerationSettingsIgnoresUnsupportedReasoningEffort(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.MAX),
        STANDARD_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
  }

  @Test
  fun applyGenerationSettingsIgnoresUnsupportedReasoningEffortButKeepsModel(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.MAX,
        ),
        STANDARD_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--model", "gpt-5.1-codex"))
  }

  @Test
  fun applyGenerationSettingsIgnoresUnsupportedPlanModeReasoningEffortButKeepsModel(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(
      bridge.applyGenerationSettings(
        baseLaunchSpec,
        AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.MAX,
        ),
        PLAN_INITIAL_MESSAGE_PLAN,
      ).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--model", "gpt-5.1-codex"))
  }

  @Test
  fun buildLaunchSpecWithInitialMessageForYoloCommand(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(
      checkNotNull(bridge.buildLaunchSpecWithInitialMessage(
        baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO),
        initialMessagePlan = AgentInitialMessagePlan(message = "-draft plan\nstep 2"),
      )).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--yolo", "--", "-draft plan\nstep 2"))
  }

  @Test
  fun buildLaunchSpecWithInitialMessageForResumeCommand(): Unit = runBlocking(Dispatchers.Default) {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("thread-1")

    assertThat(
      checkNotNull(bridge.buildLaunchSpecWithInitialMessage(
        baseLaunchSpec = resumeLaunchSpec,
        initialMessagePlan = AgentInitialMessagePlan(message = "Summarize changes"),
      )).command
    )
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("resume", "thread-1", "--", "Summarize changes"))
  }

  @Test
  fun buildLaunchSpecWithInitialMessageSkipsPlanModeStartupPrompt(): Unit = runBlocking(Dispatchers.Default) {
    val initialMessagePlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Plan this refactor",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )

    assertThat(initialMessagePlan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(initialMessagePlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(
      bridge.buildLaunchSpecWithInitialMessage(
        baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD),
        initialMessagePlan = initialMessagePlan,
      )
    ).isNull()
  }

  @Test
  fun supportsUnarchiveThread() {
    assertThat(bridge.supportsUnarchiveThread).isTrue()
  }

  @Test
  fun archiveAndRenameDelegateToThreadMutationBackend() {
    runBlocking(Dispatchers.Default) {
      var archivedPath: String? = null
      var archivedThreadId: String? = null
      var unarchivedPath: String? = null
      var unarchivedThreadId: String? = null
      var renamedPath: String? = null
      var renamedThreadId: String? = null
      var renamedName: String? = null
      val descriptor = CodexAgentSessionProviderDescriptor(
        sessionSource = emptySource(),
        threadMutationBackend = object : CodexThreadMutationBackend {
          override suspend fun archiveThread(path: String, threadId: String) {
            archivedPath = path
            archivedThreadId = threadId
          }

          override suspend fun unarchiveThread(path: String, threadId: String) {
            unarchivedPath = path
            unarchivedThreadId = threadId
          }

          override suspend fun setThreadName(path: String, threadId: String, name: String) {
            renamedPath = path
            renamedThreadId = threadId
            renamedName = name
          }
        },
      )

      assertThat(descriptor.archiveThread(path = "/tmp/project", threadId = "thread-1")).isTrue()
      assertThat(descriptor.unarchiveThread(path = "/tmp/project", threadId = "thread-1")).isTrue()
      val renameAction = checkNotNull(descriptor.threadRenameAction)
      assertThat(renameAction("/tmp/project", "thread-1", "Renamed thread"))
        .isTrue()
      assertThat(archivedPath).isEqualTo("/tmp/project")
      assertThat(archivedThreadId).isEqualTo("thread-1")
      assertThat(unarchivedPath).isEqualTo("/tmp/project")
      assertThat(unarchivedThreadId).isEqualTo("thread-1")
      assertThat(renamedPath).isEqualTo("/tmp/project")
      assertThat(renamedThreadId).isEqualTo("thread-1")
      assertThat(renamedName).isEqualTo("Renamed thread")
    }
  }

  @Test
  fun composeInitialMessageWithoutContext() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "  Refactor this  ")
    )

    assertThat(plan.message).isEqualTo("Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
  }

  @Test
  fun composeInitialMessageUsesPlainPromptBodyWhenOptionIsEnabled() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )

    assertThat(plan.message).isEqualTo("Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
  }

  @Test
  fun emptyInitialMessageCanRequestPlanMode() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )

    assertThat(plan.message).isNull()
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun composeInitialMessageTreatsManualPlanCommandAsPlainText() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = " /plan Refactor this ",
      )
    )

    assertThat(plan.message).isEqualTo("/plan Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
  }

  @Test
  fun planModeBuildsPlanModeEnsureAndPromptPostStartDispatchSteps() {
    val steps = bridge.buildPostStartDispatchSteps(
      AgentInitialMessagePlan(
        message = "Refactor this",
        mode = AgentInitialMessageMode.PLAN,
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      )
    )

    assertThat(steps).containsExactly(
      AgentInitialMessageDispatchStep(
        action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
      ),
      AgentInitialMessageDispatchStep(
        text = "Refactor this",
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      ),
    )
  }

  @Test
  fun emptyPlanModeBuildsPlanModeEnsureOnlyPostStartDispatchStep() {
    val steps = bridge.buildPostStartDispatchSteps(
      AgentInitialMessagePlan(
        message = "",
        mode = AgentInitialMessageMode.PLAN,
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      )
    )

    assertThat(steps).containsExactly(
      AgentInitialMessageDispatchStep(
        action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
      ),
    )
  }

  @Test
  fun initialMessagePlanPoliciesDependOnPlanModeAndCommand() {
    val defaultPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "Refactor this")
    )
    assertThat(defaultPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(defaultPlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(defaultPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val planModePlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )
    assertThat(planModePlan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(planModePlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(planModePlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)

    val plannerPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/planner follow-up")
    )
    assertThat(plannerPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plannerPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val manualPlanCommand = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/plan from manual input")
    )
    assertThat(manualPlanCommand.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(manualPlanCommand.message).isEqualTo("/plan from manual input")
    assertThat(manualPlanCommand.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
    assertThat(manualPlanCommand.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    val manualPlanCommandSteps = bridge.buildPostStartDispatchSteps(manualPlanCommand)
    assertThat(manualPlanCommandSteps.map { it.action }).containsExactly(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(manualPlanCommandSteps.map { it.text }).containsExactly("/plan from manual input")
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val message = messageFor(
      bridge,
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
    val message = messageFor(
      bridge,
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
    val invalidLanguage = messageFor(
      bridge,
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

    val message = messageFor(
      bridge,
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
    val message = messageFor(
      bridge,
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

private fun messageFor(bridge: CodexAgentSessionProviderDescriptor, request: AgentPromptInitialMessageRequest): String {
  return checkNotNull(bridge.buildInitialMessagePlan(request).message)
}

private val STANDARD_INITIAL_MESSAGE_PLAN: AgentInitialMessagePlan = AgentInitialMessagePlan(message = "Refactor this")

private val PLAN_INITIAL_MESSAGE_PLAN: AgentInitialMessagePlan = AgentInitialMessagePlan(
  message = "Refactor this",
  mode = AgentInitialMessageMode.PLAN,
)

private fun emptySource(): AgentSessionSource {
  return object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.CODEX

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }
}

private val CODEX_BASE_COMMAND: List<String> = listOf(
  "codex",
  "-c",
  "check_for_update_on_startup=false",
  "-c",
  "tui.terminal_title=[\"thread-id\",\"thread\"]",
)
