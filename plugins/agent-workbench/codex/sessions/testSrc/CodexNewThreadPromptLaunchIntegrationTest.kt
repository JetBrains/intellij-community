// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
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
class CodexNewThreadPromptLaunchIntegrationTest {
  @Test
  fun globalPromptNewTaskPlanModeUsesPostStartDispatch() {
    val descriptor = descriptor()

    val request = captureNewTaskPromptLaunchRequest(
      descriptor = descriptor,
      prompt = PLAN_PROMPT,
      workingProjectPath = PROJECT_PATH,
    )

    assertThat(request.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(request.projectPath).isEqualTo(PROJECT_PATH)
    assertThat(request.initialMessageRequest.prompt).isEqualTo(PLAN_PROMPT)
    assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    assertThat(request.targetThreadId).isNull()

    val observation = assertNewThreadPromptLaunchOpensNewChat(descriptor = descriptor, request = request)

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("codex:new-")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
    assertThat(observation.startupLaunchSpecOverride).isNull()
    assertThat(observation.postStartDispatchSteps.map { it.action })
      .containsExactly(AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE, AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(observation.postStartDispatchSteps.map { it.text }).containsExactly("", PLAN_PROMPT)
    assertThat(observation.initialMessageToken).isNotNull()
  }

  @Test
  fun newThreadPlanModePromptUsesLocalSessionDispatch() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = AgentPromptLaunchRequest(
        provider = AgentSessionProvider.CODEX,
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        initialMessageRequest = AgentPromptInitialMessageRequest(
          prompt = "Refactor selected code",
          providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
        ),
      ),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("codex:new-")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
    assertThat(observation.startupLaunchSpecOverride).isNull()
    assertThat(observation.postStartDispatchSteps.map { it.action })
      .containsExactly(AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE, AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(observation.postStartDispatchSteps.map { it.text }).containsExactly("", "Refactor selected code")
    assertThat(observation.initialMessageToken).isNotNull()
  }

  @Test
  fun newThreadStandardPromptStaysOnPtyCreatePath() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = AgentPromptLaunchRequest(
        provider = AgentSessionProvider.CODEX,
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        initialMessageRequest = AgentPromptInitialMessageRequest(
          prompt = "Refactor selected code",
        ),
      ),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).startsWith("codex:new-")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND)
    assertThat(observation.startupLaunchSpecOverride?.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("--", "Refactor selected code"))
    assertThat(observation.postStartDispatchSteps.single().text).isEqualTo("Refactor selected code")
    assertThat(observation.initialMessageToken).isNotNull()
  }
}

private fun descriptor(): CodexAgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.CODEX),
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
  )
}

private const val PROJECT_PATH: String = "/work/project-a"

private const val PLAN_PROMPT: String = "Plan this refactor"

private val CODEX_BASE_COMMAND: List<String> = listOf(
  "codex",
  "-c",
  "check_for_update_on_startup=false",
  "-c",
  "tui.terminal_title=[\"thread-id\",\"thread\"]",
)
