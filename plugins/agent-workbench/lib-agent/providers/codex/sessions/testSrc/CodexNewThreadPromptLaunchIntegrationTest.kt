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
    val startupBackend = RecordingThreadStartupBackend()
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
      CodexThreadStartupRequest(
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        model = null,
      )
    )
  }

  @Test
  fun newThreadPlanModePromptPrestartsPlanTurnWithGenerationSettings() {
    val startupBackend = RecordingThreadStartupBackend()
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
      CodexThreadStartupRequest(
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
      CodexPromptTurnRequest(
        threadId = RECORDED_PLAN_THREAD_ID,
        prompt = "Refactor selected code",
        mode = AgentInitialMessageMode.PLAN,
        model = "gpt-5.1-codex",
        reasoningEffort = "high",
      )
    )
  }

  @Test
  fun newThreadStandardPromptPrestartsThreadThroughAppServer() {
    val startupBackend = RecordingThreadStartupBackend()
    val descriptor = descriptor(startupBackend)

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
    assertThat(observation.identity).isEqualTo("codex:$RECORDED_PLAN_THREAD_ID")
    assertThat(observation.launchSpec.command)
      .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("resume", "--remote", REMOTE_URL, RECORDED_PLAN_THREAD_ID))
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
      CodexThreadStartupRequest(
        projectPath = PROJECT_PATH,
        launchMode = AgentSessionLaunchMode.STANDARD,
        model = null,
      )
    )

    runBlocking {
      descriptor.dispatchInitialMessageToProvider(
        AgentInitialMessageProviderDispatchRequest(
          project = ProjectManager.getInstance().defaultProject,
          projectPath = PROJECT_PATH,
          threadId = RECORDED_PLAN_THREAD_ID,
          message = "Refactor selected code",
          mode = AgentInitialMessageMode.STANDARD,
          generationSettings = AgentPromptGenerationSettings.AUTO,
        )
      )
    }
    assertThat(startupBackend.turnRequests).containsExactly(
      CodexPromptTurnRequest(
        threadId = RECORDED_PLAN_THREAD_ID,
        prompt = "Refactor selected code",
        mode = AgentInitialMessageMode.STANDARD,
        model = null,
        reasoningEffort = null,
      )
    )
  }
}

private fun descriptor(
  threadStartupBackend: CodexThreadStartupBackend = RecordingThreadStartupBackend(),
): AgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("codex")),
    threadStartupBackend = threadStartupBackend,
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
    themeLaunchConfigResolver = { null },
  ).withProvider(CODEX_AGENT_SESSION_PROVIDER)
}

internal class RecordingThreadStartupBackend : CodexThreadStartupBackend {
  override suspend fun currentRemoteUrl(): String = REMOTE_URL

  val requests: MutableList<CodexThreadStartupRequest> = mutableListOf()

  override suspend fun prestartThread(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    model: String?,
  ): CodexPrestartedThread {
    requests += CodexThreadStartupRequest(
      projectPath = projectPath,
      launchMode = launchMode,
      model = model,
    )
    return CodexPrestartedThread(
      threadId = recordingPlanPromptThreadId(requests.size),
      remoteUrl = REMOTE_URL,
    )
  }

  val turnRequests: MutableList<CodexPromptTurnRequest> = mutableListOf()

  override suspend fun startTurn(
    threadId: String,
    prompt: String,
    mode: AgentInitialMessageMode,
    model: String?,
    reasoningEffort: String?,
  ) {
    turnRequests += CodexPromptTurnRequest(
      threadId = threadId,
      prompt = prompt,
      mode = mode,
      model = model,
      reasoningEffort = reasoningEffort,
    )
  }
}

internal data class CodexThreadStartupRequest(
  @JvmField val projectPath: String,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val model: String?,
)

internal data class CodexPromptTurnRequest(
  @JvmField val threadId: String,
  @JvmField val prompt: String,
  @JvmField val mode: AgentInitialMessageMode,
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
