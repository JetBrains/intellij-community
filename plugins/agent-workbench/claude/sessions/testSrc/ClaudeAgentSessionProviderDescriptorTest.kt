// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ClaudeAgentSessionProviderDescriptorTest {
  private val bridge = ClaudeAgentSessionProviderDescriptor()

  @Test
  fun buildNewEntryLaunchSpec() {
    assertThat(bridge.buildNewEntryLaunchSpec().command)
      .containsExactly("claude", "--permission-mode", "default")
    assertThat(bridge.buildNewEntryLaunchSpec().envVariables)
      .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
  }

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
  fun buildLaunchSpecWithInitialPromptAddsPermissionModeDefault() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialPrompt(baseLaunchSpec, "Refactor this")

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "default", "--", "Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptSwitchesToPlanMode() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialPrompt(baseLaunchSpec, "/plan Refactor this")

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "plan", "--", "Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptDoesNotTreatPlannerAsPlanMode() {
    val baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = bridge.buildLaunchSpecWithInitialPrompt(baseLaunchSpec, "/planner Refactor this")

    assertThat(launchSpec.command)
      .containsExactly("claude", "--permission-mode", "default", "--", "/planner Refactor this")
  }

  @Test
  fun buildLaunchSpecWithInitialPromptForResumeCommand() {
    val resumeLaunchSpec = bridge.buildResumeLaunchSpec("session-1")

    val launchSpec = bridge.buildLaunchSpecWithInitialPrompt(resumeLaunchSpec, "-summarize\nchanges")

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
  fun composeInitialMessagePrefixesPlanCommandWhenProviderOptionIsEnabled() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("/plan Refactor this")
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun composeInitialMessagePrefixesPlanCommandWhenLegacyFlagIsEnabled() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Refactor this",
        planModeEnabled = true,
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("/plan Refactor this")
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun composeInitialMessageDoesNotDoublePrefixPlanCommand() {
    val plan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = " /plan Refactor this ",
        planModeEnabled = true,
      )
    )
    val message = checkNotNull(plan.message)

    assertThat(message).isEqualTo("/plan Refactor this")
  }

  @Test
  fun initialMessagePlanPoliciesDefaultWithoutPlanMode() {
    val defaultPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "Refactor this")
    )

    assertThat(defaultPlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(defaultPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val plannerPlan = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/planner follow-up")
    )
    assertThat(plannerPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

    val manualPlanCommand = bridge.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "/plan follow-up")
    )
    assertThat(manualPlanCommand.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(manualPlanCommand.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
  }

  @Test
  fun promptOptionsUseSharedPlanModeOption() {
    assertThat(bridge.promptOptions).containsExactly(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)
  }
}
