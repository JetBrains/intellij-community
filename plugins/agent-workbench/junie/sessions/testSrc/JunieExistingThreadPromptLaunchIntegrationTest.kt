// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.junie.common.JunieCliInfo
import com.intellij.agent.workbench.junie.common.JunieCliSupport
import com.intellij.agent.workbench.junie.common.JunieCliVersion
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertExistingThreadLaunchUsesPostStartDispatch
import com.intellij.agent.workbench.sessions.assertExistingThreadLaunchUsesStartupOverride
import com.intellij.agent.workbench.sessions.existingThreadPromptLaunchRequest
import com.intellij.agent.workbench.sessions.thread
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class JunieExistingThreadPromptLaunchIntegrationTest {
  @Test
  fun existingThreadPlanModePromptUsesStartupPlanPromptCommand() {
    val observation = assertExistingThreadLaunchUsesStartupOverride(
      descriptor = descriptor(),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.JUNIE,
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
        planMode = true,
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )

    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--session-id",
      EXISTING_THREAD_ID,
      "--plan",
      "--prompt",
      observation.postStartDispatchSteps.last().text,
    )
  }

  @Test
  fun existingThreadPlanModePromptGenerationSettingsUseStartupPlanPromptCommand() {
    val observation = assertExistingThreadLaunchUsesStartupOverride(
      descriptor = descriptor(),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.JUNIE,
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
        planMode = true,
      ).copy(
        generationSettings = AgentPromptGenerationSettings(
          modelId = "sonnet",
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )

    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--session-id",
      EXISTING_THREAD_ID,
      "--model",
      "sonnet",
      "--effort",
      "high",
      "--plan",
      "--prompt",
      observation.postStartDispatchSteps.last().text,
    )
  }

  @Test
  fun oldJunieExistingThreadPlanModePromptUsesPostStartDispatch() {
    assertExistingThreadLaunchUsesPostStartDispatch(
      descriptor = descriptor(cliVersion = JunieCliVersion(1962, 1)),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.JUNIE,
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
        planMode = true,
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )
  }
}

private fun descriptor(cliVersion: JunieCliVersion? = JunieCliVersion(1963, 1)): JunieAgentSessionProviderDescriptor {
  return JunieAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.JUNIE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = EXISTING_THREAD_ID, updatedAt = 200, provider = AgentSessionProvider.JUNIE))
        }
        else {
          emptyList()
        }
      },
    ),
    executableResolver = { JunieCliSupport.JUNIE_COMMAND },
    cliInfoResolver = { JunieCliInfo(JunieCliSupport.JUNIE_COMMAND, cliVersion) },
  )
}

private const val PROJECT_PATH: String = "/work/project-a"
private const val EXISTING_THREAD_ID: String = "thread-existing"
