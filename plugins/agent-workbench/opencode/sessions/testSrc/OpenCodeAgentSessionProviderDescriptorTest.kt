// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions

import com.intellij.agent.workbench.core.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenCodeAgentSessionProviderDescriptorTest {
  private val descriptor = OpenCodeAgentSessionProviderDescriptor(
    executableResolver = { "opencode" },
    cliAvailableProbe = { true },
  )

  @Test
  fun exposesOpenCodeProviderWithNormalLaunchMode() {
    assertThat(descriptor.provider).isEqualTo(AgentSessionProvider.OPENCODE)
    assertThat(descriptor.supportedLaunchModes).containsExactly(AgentSessionLaunchMode.STANDARD)
    assertThat(descriptor.cliVisibilityPolicy).isEqualTo(AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE)
    assertThat(descriptor.yoloSessionLabelKey).isNull()
    assertThat(descriptor.yoloSessionModeLabelKey).isNull()
  }

  @Test
  fun buildNewSessionLaunchSpecUsesOpenCodeCommand(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly("opencode")
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun buildResumeLaunchSpecPassesSessionId(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildResumeLaunchSpec("thread-1", AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly("opencode", "--session", "thread-1")
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun exposesModelAndReasoningEffortSelection() {
    assertThat(descriptor.supportsGenerationModelSelection).isTrue()
    assertThat(descriptor.supportedReasoningEfforts).containsExactlyInAnyOrder(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.MAX,
    )
  }

  @Test
  fun applyGenerationSettingsPassesModelAndVariantBeforePrompt(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD),
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings(
        modelId = "anthropic/claude-sonnet-4",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )

    assertThat(launchSpec.command).containsExactly(
      "opencode",
      "--model",
      "anthropic/claude-sonnet-4",
      "--variant",
      "high",
      "--prompt",
      "Refactor this",
    )
  }

  @Test
  fun applyGenerationSettingsReplacesPreviousModelAndVariant(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD),
      generationSettings = AgentPromptGenerationSettings(modelId = "openai/gpt-5", reasoningEffort = AgentPromptReasoningEffort.LOW),
      initialMessagePlan = AgentInitialMessagePlan.EMPTY,
    )

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings(
        modelId = "anthropic/claude-sonnet-4",
        reasoningEffort = AgentPromptReasoningEffort.MAX,
      ),
      initialMessagePlan = AgentInitialMessagePlan.EMPTY,
    )

    assertThat(launchSpec.command).containsExactly(
      "opencode",
      "--model",
      "anthropic/claude-sonnet-4",
      "--variant",
      "max",
    )
  }

  @Test
  fun applyGenerationSettingsStripsStaleFlagsBeforePromptWithoutTouchingPromptText(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf(
        "opencode",
        "--model",
        "old-model",
        "--variant",
        "low",
        "--model",
        "stale-model",
        "--variant",
        "--prompt",
        "keep --model as prompt text",
      ),
    )

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings(
        modelId = "anthropic/claude-sonnet-4",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )

    assertThat(launchSpec.command).containsExactly(
      "opencode",
      "--model",
      "anthropic/claude-sonnet-4",
      "--variant",
      "high",
      "--prompt",
      "keep --model as prompt text",
    )
  }

  @Test
  fun buildLaunchSpecWithInitialMessagePassesPromptFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD),
      initialMessagePlan = AgentInitialMessagePlan(message = "Refactor this"),
    )

    assertThat(launchSpec.command).containsExactly("opencode", "--prompt", "Refactor this")
  }

  @Test
  fun buildInitialMessagePlanComposesPromptContext() {
    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = "Refactor this"),
    )

    assertThat(plan.message).isEqualTo("Refactor this")
  }

}
