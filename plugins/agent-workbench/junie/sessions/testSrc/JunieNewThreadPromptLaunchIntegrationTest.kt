// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.junie.common.JunieCliInfo
import com.intellij.agent.workbench.junie.common.JunieCliSupport
import com.intellij.agent.workbench.junie.common.JunieCliVersion
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertNewThreadPromptLaunchOpensNewChat
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
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

    assertThat(request.provider).isEqualTo(AgentSessionProvider.JUNIE)
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
    assertThat(observation.postStartDispatchSteps.single().action).isEqualTo(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(observation.postStartDispatchSteps.single().text).isEqualTo("Implement the Junie flow")
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
    assertThat(observation.postStartDispatchSteps.single().action).isEqualTo(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(observation.postStartDispatchSteps.single().text).isEqualTo("Implement the Junie flow")
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
    assertThat(observation.postStartDispatchSteps.map { it.action }).containsExactly(
      AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
      AgentInitialMessageDispatchAction.SEND_TEXT,
    )
    assertThat(observation.postStartDispatchSteps.map { it.text }).containsExactly("", "Plan the Junie flow")
    assertThat(observation.initialMessageToken).isNotNull()
  }

  @Test
  fun oldJunieNewThreadPlanModePromptUsesTerminalPlanModeDispatch() {
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
    assertThat(observation.postStartDispatchSteps.map { it.action }).containsExactly(
      AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
      AgentInitialMessageDispatchAction.SEND_TEXT,
    )
    assertThat(observation.postStartDispatchSteps.map { it.text }).containsExactly("", "Plan the Junie flow")
    assertThat(observation.initialMessageToken).isNotNull()
  }
}

private fun newThreadLaunchRequest(
  prompt: String,
  providerOptionIds: Set<String> = emptySet(),
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = AgentSessionProvider.JUNIE,
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

private fun descriptor(cliVersion: JunieCliVersion? = JunieCliVersion(1963, 1)): JunieAgentSessionProviderDescriptor {
  return JunieAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.JUNIE),
    executableResolver = { JunieCliSupport.JUNIE_COMMAND },
    cliInfoResolver = { JunieCliInfo(JunieCliSupport.JUNIE_COMMAND, cliVersion) },
  )
}

private const val PROJECT_PATH: String = "/work/project-a"
