// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertNewThreadPromptLaunchOpensNewChat
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageProviderDispatchRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.withProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexNewThreadPromptLaunchIntegrationTest {
  @Test
  fun globalPromptNewTaskPlanModePrestartsPlanTurnThroughAppServer() {
    val startupBackend = RecordingPlanPromptStartupBackend()
    val descriptor = descriptor(startupBackend)

    val request = captureNewTaskPromptLaunchRequest(
      descriptor = descriptor,
      prompt = PLAN_PROMPT,
      workingProjectPath = PROJECT_PATH,
    )

    assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
    assertThat(request.projectPath).isEqualTo(PROJECT_PATH)
    assertThat(request.initialMessageRequest.prompt).isEqualTo(PLAN_PROMPT)
    assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    assertThat(request.targetThreadId).isNull()

    val observation = assertNewThreadPromptLaunchOpensNewChat(descriptor = descriptor, request = request)

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).isEqualTo("codex:$RECORDED_PLAN_THREAD_ID")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("resume", "--remote", REMOTE_URL, RECORDED_PLAN_THREAD_ID))
    assertThat(observation.launchSpec.preallocatedSessionId).isEqualTo(RECORDED_PLAN_THREAD_ID)
    assertThat(observation.startupLaunchSpecOverride).isNull()
    val dispatchStep = observation.postStartDispatchSteps.single()
    assertThat(dispatchStep.action).isEqualTo(AgentInitialMessageDispatchAction.PROVIDER)
    assertThat(dispatchStep.text).isEqualTo(PLAN_PROMPT)
    assertThat(observation.initialPromptMessage).isEqualTo(PLAN_PROMPT)
    assertThat(observation.initialMessageToken).isNull()
    assertThat(observation.initialPromptDeliveryStatus).isEqualTo(AgentInitialPromptDeliveryStatus.PENDING)
    assertThat(observation.initialPromptDeliveryChannel).isEqualTo(AgentInitialPromptDeliveryChannel.APP_SERVER)
    assertThat(startupBackend.requests).containsExactly(
      CodexPlanPromptStartupRequest(
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        model = null,
      )
    )
  }

  @Test
  fun newThreadPlanModePromptPrestartsPlanTurnWithGenerationSettings() {
    val startupBackend = RecordingPlanPromptStartupBackend()
    val descriptor = descriptor(startupBackend)

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = AgentPromptLaunchRequest(
        provider = AgentSessionProvider.from("codex"),
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        generationSettings = AgentPromptGenerationSettings(
          modelId = "gpt-5.1-codex",
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
        generationModelCatalog = listOf(
          AgentPromptGenerationModel(
            id = "gpt-5.1-codex",
            displayName = "GPT-5.1 Codex",
            supportedReasoningEfforts = setOf(AgentPromptReasoningEffort.HIGH),
          ),
        ),
        initialMessageRequest = AgentPromptInitialMessageRequest(
          prompt = "Refactor selected code",
          providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
        ),
      ),
    )

    assertThat(observation.normalizedPath).isEqualTo(PROJECT_PATH)
    assertThat(observation.identity).isEqualTo("codex:$RECORDED_PLAN_THREAD_ID")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(
        CODEX_BASE_COMMAND + listOf(
          "--model",
          "gpt-5.1-codex",
          "-c",
          "model_reasoning_effort=\"high\"",
          "-c",
          "plan_mode_reasoning_effort=\"high\"",
          "resume",
          "--remote",
          REMOTE_URL,
          RECORDED_PLAN_THREAD_ID,
        )
      )
    assertThat(observation.launchSpec.preallocatedSessionId).isEqualTo(RECORDED_PLAN_THREAD_ID)
    assertThat(observation.startupLaunchSpecOverride).isNull()
    val dispatchStep = observation.postStartDispatchSteps.single()
    assertThat(dispatchStep.action).isEqualTo(AgentInitialMessageDispatchAction.PROVIDER)
    assertThat(dispatchStep.text).isEqualTo("Refactor selected code")
    assertThat(observation.initialPromptMessage).isEqualTo("Refactor selected code")
    assertThat(observation.initialMessageToken).isNull()
    assertThat(observation.initialPromptDeliveryStatus).isEqualTo(AgentInitialPromptDeliveryStatus.PENDING)
    assertThat(observation.initialPromptDeliveryChannel).isEqualTo(AgentInitialPromptDeliveryChannel.APP_SERVER)
    assertThat(startupBackend.requests).containsExactly(
      CodexPlanPromptStartupRequest(
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        model = "gpt-5.1-codex",
      )
    )

    runBlocking {
      descriptor.dispatchInitialMessageToProvider(
        AgentInitialMessageProviderDispatchRequest(
          project = ProjectManager.getInstance().defaultProject,
          projectPath = PROJECT_PATH,
          threadId = RECORDED_PLAN_THREAD_ID,
          message = "Refactor selected code",
          mode = AgentInitialMessageMode.PLAN,
          generationSettings = AgentPromptGenerationSettings(
            modelId = "gpt-5.1-codex",
            reasoningEffort = AgentPromptReasoningEffort.HIGH,
          ),
        )
      )
    }
    assertThat(startupBackend.turnRequests).containsExactly(
      CodexPlanPromptTurnRequest(
        threadId = RECORDED_PLAN_THREAD_ID,
        prompt = "Refactor selected code",
        model = "gpt-5.1-codex",
        reasoningEffort = "high",
      )
    )
  }

  @Test
  fun newThreadStandardPromptStaysOnPtyCreatePath() {
    val descriptor = descriptor()

    val observation = assertNewThreadPromptLaunchOpensNewChat(
      descriptor = descriptor,
      request = AgentPromptLaunchRequest(
        provider = AgentSessionProvider.from("codex"),
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
    assertThat(observation.postStartDispatchSteps).isEmpty()
    assertThat(observation.initialPromptMessage).isEqualTo("Refactor selected code")
    assertThat(observation.initialMessageToken).isNotNull()
  }
}

private fun descriptor(
  planPromptStartupBackend: CodexPlanPromptStartupBackend = RecordingPlanPromptStartupBackend(),
): AgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("codex")),
    planPromptStartupBackend = planPromptStartupBackend,
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
  ).withProvider(CODEX_AGENT_SESSION_PROVIDER)
}

internal class RecordingPlanPromptStartupBackend : CodexPlanPromptStartupBackend {
  val requests: MutableList<CodexPlanPromptStartupRequest> = mutableListOf()

  override suspend fun prestartPlanPromptThread(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    model: String?,
  ): CodexPrestartedPlanPrompt {
    requests += CodexPlanPromptStartupRequest(
      projectPath = projectPath,
      launchMode = launchMode,
      model = model,
    )
    return CodexPrestartedPlanPrompt(
      threadId = recordingPlanPromptThreadId(requests.size),
      remoteUrl = REMOTE_URL,
    )
  }

  val turnRequests: MutableList<CodexPlanPromptTurnRequest> = mutableListOf()

  override suspend fun startPlanPromptTurn(
    threadId: String,
    prompt: String,
    model: String?,
    reasoningEffort: String?,
  ) {
    turnRequests += CodexPlanPromptTurnRequest(
      threadId = threadId,
      prompt = prompt,
      model = model,
      reasoningEffort = reasoningEffort,
    )
  }
}

internal data class CodexPlanPromptStartupRequest(
  @JvmField val projectPath: String,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val model: String?,
)

internal data class CodexPlanPromptTurnRequest(
  @JvmField val threadId: String,
  @JvmField val prompt: String,
  @JvmField val model: String?,
  @JvmField val reasoningEffort: String?,
)

private const val PROJECT_PATH: String = "/work/project-a"

private const val PLAN_PROMPT: String = "Plan this refactor"

internal const val REMOTE_URL: String = "ws://127.0.0.1:54321"

internal const val RECORDED_PLAN_THREAD_ID: String = "019effd8-0000-7000-8000-000000000001"

private fun recordingPlanPromptThreadId(index: Int): String {
  return "019effd8-0000-7000-8000-${index.toString().padStart(12, '0')}"
}

private val CODEX_BASE_COMMAND: List<String> = listOf(
  "codex",
  "-c",
  "check_for_update_on_startup=false",
  "-c",
  "tui.terminal_title=[\"thread-id\",\"thread\"]",
)
