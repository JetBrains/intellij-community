// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameHandler
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ClaudeAgentSessionProviderDescriptorTest {
  private val bridge = ClaudeAgentSessionProviderDescriptor()

  @Test
  fun buildResumeLaunchSpec() {
    assertThat(bridge.buildResumeLaunchSpec("session-1").command)
      .containsExactly("claude", "--resume", "session-1")
    assertThat(bridge.buildResumeLaunchSpec("session-1").envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

  @Test
  fun buildYoloLaunchSpec() {
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO).command)
      .containsExactly("claude", "--dangerously-skip-permissions")
  }

  @Test
  fun buildStandardLaunchSpec() {
    assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).command)
      .containsExactly("claude", "--permission-mode", "default")
  }

  @Test
  fun enablesPendingEditorTabRebind() {
    assertThat(bridge.supportsPendingEditorTabRebind).isTrue()
    assertThat(bridge.emitsScopedRefreshSignals).isTrue()
    assertThat(bridge.refreshPathAfterCreateNewSession).isTrue()
    assertThat(bridge.supportsNewThreadRebind).isFalse()
    assertThat(bridge.editorTabActionIds)
      .containsExactly(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)
  }

  @Test
  fun renameThreadHandlerUsesSharedDispatchContract() {
    val renameHandler = bridge.threadRenameHandler

    assertThat(renameHandler).isInstanceOf(AgentThreadRenameHandler.ChatDispatch::class.java)
    renameHandler as AgentThreadRenameHandler.ChatDispatch
    assertThat(renameHandler.supportedContexts)
      .containsExactlyInAnyOrder(AgentThreadRenameContext.TREE_POPUP, AgentThreadRenameContext.EDITOR_TAB)
    assertThat(checkNotNull(renameHandler.buildDispatchPlan("Renamed thread")).postStartDispatchSteps.map { it.text })
      .containsExactly("/rename Renamed thread")
  }

  @Test
  fun buildLaunchSpecWithInitialMessageAddsPermissionModeDefault() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "default", "--", "Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialMessageSwitchesToPlanMode() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(
        message = "Refactor this",
        mode = AgentInitialMessageMode.PLAN,
      ),
    )

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "plan", "--", "Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialMessageDoesNotTreatPlannerAsPlanMode() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "/planner Refactor this"),
    )

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "default", "--", "/planner Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialMessageForResumeCommand() {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("session-1")

    val launchSpec = bridge.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = resumeLaunchSpec,
      initialMessagePlan = AgentInitialMessagePlan(message = "-summarize\nchanges"),
    )

    assertThat(launchSpec.command)
      .containsExactly("claude", "--resume", "session-1", "--permission-mode", "default", "--", "-summarize\nchanges")
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
  fun composeInitialMessageStripsManualPlanCommandPrefix() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = " /plan Refactor this ",
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("Refactor this")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
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

    val manualPlanCommand = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/plan follow-up")
    )
    assertThat(manualPlanCommand.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(manualPlanCommand.message).isEqualTo("follow-up")
    assertThat(manualPlanCommand.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(manualPlanCommand.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
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
}
