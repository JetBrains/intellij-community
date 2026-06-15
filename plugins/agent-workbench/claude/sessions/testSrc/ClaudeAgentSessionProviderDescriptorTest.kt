// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.UUID

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeAgentSessionProviderDescriptorTest {
  private val bridge = ClaudeAgentSessionProviderDescriptor(
    executableResolver = { ClaudeCliSupport.CLAUDE_COMMAND },
  )

  private fun assertValidUuid(sessionId: String?): String {
    val value = checkNotNull(sessionId)
    assertThat(UUID.fromString(value).toString()).isEqualTo(value)
    return value
  }

  @Test
  fun buildResumeLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(bridge.buildResumeLaunchSpec("session-1").command)
      .containsExactly("claude", "--resume", "session-1")
    assertThat(bridge.buildResumeLaunchSpec("session-1").envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildYoloResumeLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    assertThat(bridge.buildResumeLaunchSpec("session-1", AgentSessionLaunchMode.YOLO).command)
      .containsExactly("claude", "--resume", "session-1", "--dangerously-skip-permissions")
    assertThat(bridge.buildResumeLaunchSpec("session-1", AgentSessionLaunchMode.YOLO).envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildYoloLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO)
    val sessionId = assertValidUuid(launchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--dangerously-skip-permissions", "--session-id", sessionId)
  }

  @Test
  fun buildStandardLaunchSpec(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)
    val sessionId = assertValidUuid(launchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--session-id", sessionId)
  }

  @Test
  fun applyGenerationSettingsLeavesAutoLaunchSpecUnchanged(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)
    val sessionId = assertValidUuid(launchSpec.preallocatedSessionId)

    val updatedLaunchSpec = bridge.applyGenerationSettings(launchSpec, AgentPromptGenerationSettings.AUTO, STANDARD_INITIAL_MESSAGE_PLAN)

    assertThat(updatedLaunchSpec.command)
      .containsExactly("claude", "--session-id", sessionId)
    assertThat(updatedLaunchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun applyGenerationSettingsAddsReasoningEffortFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)
    val sessionId = assertValidUuid(launchSpec.preallocatedSessionId)

    val updatedLaunchSpec = bridge.applyGenerationSettings(
      launchSpec,
      AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.MAX),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command)
      .containsExactly("claude", "--session-id", sessionId, "--effort", "max")
    assertThat(updatedLaunchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun applyGenerationSettingsIgnoresPlanReasoningEffort(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)
    val sessionId = assertValidUuid(launchSpec.preallocatedSessionId)

    val updatedLaunchSpec = bridge.applyGenerationSettings(
      launchSpec,
      AgentPromptGenerationSettings(planReasoningEffort = AgentPromptReasoningEffort.MAX),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command)
      .containsExactly("claude", "--session-id", sessionId)
    assertThat(updatedLaunchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun replaceOrAddEffortInsertsBeforePromptSeparator() {
    assertThat(replaceOrAddEffort(listOf("claude", "--", "Refactor this"), "xhigh"))
      .containsExactly("claude", "--effort", "xhigh", "--", "Refactor this")
  }

  @Test
  fun replaceOrAddEffortReplacesExistingFlag() {
    assertThat(replaceOrAddEffort(listOf("claude", "--effort", "low", "--session-id", "session-1"), "high"))
      .containsExactly("claude", "--effort", "high", "--session-id", "session-1")
  }

  @Test
  fun enablesPendingEditorTabRebind() {
    assertThat(bridge.supportsPendingEditorTabRebind).isTrue()
    assertThat(bridge.emitsScopedRefreshSignals).isTrue()
    assertThat(bridge.refreshPathAfterCreateNewSession).isTrue()
    assertThat(bridge.supportsNewThreadRebind).isFalse()
    assertThat(bridge.supportsArchiveThread).isTrue()
    assertThat(bridge.closeOpenChatBeforeArchiveThread).isTrue()
    assertThat(bridge.supportsUnarchiveThread).isTrue()
    assertThat(bridge.suppressArchivedThreadsDuringRefresh).isTrue()
    assertThat(bridge.archiveRefreshDelayMs).isEqualTo(1_000L)
    assertThat(bridge.editorTabActionIds)
      .containsExactly(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)
  }

  @Test
  fun renameThreadActionIsAvailable() {
    assertThat(bridge.threadRenameAction).isNotNull
  }

  @Test
  fun buildLaunchSpecWithInitialMessageDoesNotForcePermissionModeDefault(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )
    val sessionId = checkNotNull(baseLaunchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--session-id", sessionId, "--", "Refactor this")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun buildLaunchSpecWithInitialMessageSwitchesToPlanMode(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(
        message = "Refactor this",
        mode = AgentInitialMessageMode.PLAN,
      ),
    )
    val sessionId = checkNotNull(baseLaunchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--session-id", sessionId, "--permission-mode", "plan", "--", "Refactor this")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun buildLaunchSpecWithEmptyPlanModeSwitchesToPlanMode(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = null, mode = AgentInitialMessageMode.PLAN),
    )
    val sessionId = checkNotNull(baseLaunchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--session-id", sessionId, "--permission-mode", "plan")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun buildLaunchSpecWithInitialMessageDoesNotTreatPlannerAsPlanMode(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "/planner Refactor this"),
    )
    val sessionId = checkNotNull(baseLaunchSpec.preallocatedSessionId)

    assertThat(launchSpec.command)
      .containsExactly("claude", "--session-id", sessionId, "--", "/planner Refactor this")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo(sessionId)
  }

  @Test
  fun buildLaunchSpecWithInitialMessageForResumeCommand(): Unit = runBlocking(Dispatchers.Default) {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("session-1")

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = resumeLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "-summarize\nchanges"),
    )

    assertThat(launchSpec.command)
      .containsExactly("claude", "--resume", "session-1", "--", "-summarize\nchanges")
    assertThat(launchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun composeInitialMessageUsesCompactContextBlock() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Summarize changes",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Project Selection",
            body = "file: /tmp/demo.kt",
            source = "projectView",
          )
        ),
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
    assertThat(message).startsWith("Summarize changes\n\n### IDE Context")
    assertThat(message).contains("path: /tmp/demo.kt")
    assertThat(message).doesNotContain("soft-cap:")
    assertThat(message).doesNotContain("Metadata:")
    assertThat(message).doesNotContain("Items:")
    assertThat(message).doesNotContain("\"schema\"")
  }

  @Test
  fun composeInitialMessageUsesPlainPromptBodyWhenProviderOptionIsEnabled() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun composeInitialMessageTreatsManualPlanCommandAsPlainText() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = " /plan Refactor this ",
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("/plan Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
  }

  @Test
  fun initialMessagePlanPoliciesDefaultWithoutPlanMode() {
    val defaultPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "Refactor this")
    )

    assertThat(defaultPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(defaultPlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(defaultPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val plannerPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/planner follow-up")
    )
    assertThat(plannerPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plannerPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val manualPlanText = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/plan follow-up")
    )
    assertThat(manualPlanText.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(manualPlanText.message).isEqualTo("/plan follow-up")
    assertThat(manualPlanText.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(manualPlanText.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
  }

  @Test
  fun menuCommandsUsePostStartDeliveryWithoutContextEnvelope() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "  /model sonnet  ",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Project Selection",
            body = "file: /tmp/demo.kt",
            source = "projectView",
          )
        ),
      )
    )

    assertThat(plan.message).isEqualTo("/model sonnet")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
  }

  @Test
  fun promptOptionsUseSharedPlanModeOption() {
    assertThat(bridge.promptOptions).containsExactly(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)
  }

  @Test
  fun archiveAndRenameDelegateToRenameEngine() {
    runBlocking(Dispatchers.Default) {
      var archivedPath: String? = null
      var archivedThreadId: String? = null
      var unarchivedPath: String? = null
      var unarchivedThreadId: String? = null
      var renamedPath: String? = null
      var renamedThreadId: String? = null
      var renamedNewTitle: String? = null
      val descriptor = ClaudeAgentSessionProviderDescriptor(
        backend = emptyBackend(),
        sessionSource = emptySource(),
        renameEngine = object : ClaudeThreadRenameEngine {
          override suspend fun rename(path: String, threadId: String, newTitle: String): Boolean {
            renamedPath = path
            renamedThreadId = threadId
            renamedNewTitle = newTitle
            return true
          }

          override suspend fun archiveThread(path: String, threadId: String): Boolean {
            archivedPath = path
            archivedThreadId = threadId
            return true
          }

          override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
            unarchivedPath = path
            unarchivedThreadId = threadId
            return true
          }
        },
      )

      assertThat(descriptor.archiveThread(path = "/tmp/project", threadId = "session-1")).isTrue()
      assertThat(descriptor.unarchiveThread(path = "/tmp/project", threadId = "session-1")).isTrue()
      val renameAction = checkNotNull(descriptor.threadRenameAction)
      assertThat(renameAction("/tmp/project", "session-1", "Renamed thread")).isTrue()
      assertThat(archivedPath).isEqualTo("/tmp/project")
      assertThat(archivedThreadId).isEqualTo("session-1")
      assertThat(unarchivedPath).isEqualTo("/tmp/project")
      assertThat(unarchivedThreadId).isEqualTo("session-1")
      assertThat(renamedPath).isEqualTo("/tmp/project")
      assertThat(renamedThreadId).isEqualTo("session-1")
      assertThat(renamedNewTitle).isEqualTo("Renamed thread")
    }
  }
}

private val STANDARD_INITIAL_MESSAGE_PLAN: AgentInitialMessagePlan = AgentInitialMessagePlan(message = "Refactor this")

private fun emptyBackend(): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = emptyList()
  }
}

private fun emptySource(): AgentSessionSource {
  return object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.CLAUDE

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()

    override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
      get() = emptyFlow()
  }
}
