// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.junie.common.JunieCliInfo
import com.intellij.platform.ai.agent.junie.common.JunieCliSupport
import com.intellij.platform.ai.agent.junie.common.JunieCliVersion
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertNewThreadPromptLaunchOpensNewChat
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.withProvider
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class JunieNewThreadPromptLaunchIntegrationTest {
  @Test
  fun globalPromptNewTaskUsesStartupPromptCommand() {
    val descriptor = descriptor()

    val request = captureNewTaskPromptLaunchRequest(
      descriptor = descriptor,
      prompt = "Implement the Junie flow",
      workingProjectPath = PROJECT_PATH,
    )

    assertThat(request.provider).isEqualTo(AgentSessionProvider.from("junie"))
    assertThat(request.projectPath).isEqualTo(PROJECT_PATH)
    assertThat(request.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(request.initialMessageRequest.prompt).isEqualTo("Implement the Junie flow")
    assertThat(request.initialMessageRequest.providerOptionIds).isEmpty()
    assertThat(request.targetThreadId).isNull()

    val observation = assertNewThreadPromptLaunchOpensNewChat(descriptor = descriptor, request = request)

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("junie:new-")
    assertThat(observation.launchSpec.command).containsExactly("junie", "--skip-update-check")
    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--prompt",
      "Implement the Junie flow",
    )
    assertThat(observation.postStartDispatchSteps).isEmpty()
    assertThat(observation.initialPromptMessage).isEqualTo("Implement the Junie flow")
    assertThat(observation.initialMessageToken).isNotNull()
  }

  @Test
  fun newThreadStandardPromptUsesStartupPromptCommand() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = newThreadLaunchRequest(prompt = "Implement the Junie flow"),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("junie:new-")
    assertThat(observation.launchSpec.command).containsExactly("junie", "--skip-update-check")
    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--prompt",
      "Implement the Junie flow",
    )
    assertThat(observation.postStartDispatchSteps).isEmpty()
    assertThat(observation.initialPromptMessage).isEqualTo("Implement the Junie flow")
    assertThat(observation.initialMessageToken).isNotNull()
  }

  @Test
  fun newThreadPromptGenerationSettingsUseStartupPromptCommand() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = newThreadLaunchRequest(
        prompt = "Implement the Junie flow",
        generationSettings = AgentPromptGenerationSettings(
          modelId = "gpt-codex",
          reasoningEffort = AgentPromptReasoningEffort.XHIGH,
        ),
      ),
    )

    assertThat(observation.launchSpec.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--model",
      "gpt-codex",
      "--effort",
      "xhigh",
    )
    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--model",
      "gpt-codex",
      "--effort",
      "xhigh",
      "--prompt",
      "Implement the Junie flow",
    )
  }

  @Test
  fun newThreadPlanModePromptUsesStartupPlanPromptCommand() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = newThreadLaunchRequest(
        prompt = "Plan the Junie flow",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      ),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("junie:new-")
    assertThat(observation.launchSpec.command).containsExactly("junie", "--skip-update-check")
    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--plan",
      "--prompt",
      "Plan the Junie flow",
    )
    assertThat(observation.postStartDispatchSteps).isEmpty()
    assertThat(observation.initialPromptMessage).isEqualTo("Plan the Junie flow")
    assertThat(observation.initialMessageToken).isNull()
  }

  @Test
  fun oldJunieNewThreadPlanModePromptIsNotDispatched() {
    val descriptor = descriptor(cliVersion = JunieCliVersion(1962, 1))

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = newThreadLaunchRequest(
        prompt = "Plan the Junie flow",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      ),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("junie:new-")
    assertThat(observation.launchSpec.command).containsExactly("junie", "--skip-update-check")
    assertThat(observation.startupLaunchSpecOverride).isNull()
    assertThat(observation.postStartDispatchSteps).isEmpty()
    assertThat(observation.initialPromptMessage).isNull()
    assertThat(observation.initialMessageToken).isNull()
  }
}

private fun newThreadLaunchRequest(
  prompt: String,
  providerOptionIds: Set<String> = emptySet(),
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = AgentSessionProvider.from("junie"),
    projectPath = PROJECT_PATH,
    launchMode = AgentSessionLaunchMode.STANDARD,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = prompt,
      providerOptionIds = providerOptionIds,
    ),
    targetThreadId = null,
    preferredDedicatedFrame = null,
    generationSettings = generationSettings,
  )
}

private fun descriptor(cliVersion: JunieCliVersion? = JunieCliVersion(2030, 1)): AgentSessionProviderDescriptor {
  return JunieAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("junie")),
    executableResolver = { JunieCliSupport.JUNIE_COMMAND },
    cliInfoResolver = { JunieCliInfo(JunieCliSupport.JUNIE_COMMAND, cliVersion) },
  ).withProvider(JUNIE_AGENT_SESSION_PROVIDER)
}

private const val PROJECT_PATH: String = "/work/project-a"
