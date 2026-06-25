// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.platform.ai.agent.codex.common.CodexTurnCollaborationMode
import com.intellij.platform.ai.agent.codex.common.CodexWebSocketAppServerClient
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexPlanPromptRealAppServerIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun realTuiRemoteResumeAttachesToPlanPromptStartedByRealAppServer() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      val prompt = "Plan this real remote resume integration test."
      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("plan-remote-resume"),
        responsePlans = listOf(
          MockResponsesPlan.completedAssistantMessage(PROPOSED_PLAN_RESPONSE),
        ),
      ).use { harness ->
        val client = CodexWebSocketAppServerClient(
          coroutineScope = this,
          executablePathProvider = { codexBinary },
          environmentOverrides = mapOf("CODEX_HOME" to harness.codexHome.toString()),
          workingDirectory = harness.projectDir,
        )
        try {
          val session = client.createThreadSession(cwd = harness.projectDir.toString())
          val threadId = session.thread.id
          client.materializeThread(threadId)
          val remoteUrl = client.currentRemoteUrl()
          assertThat(harness.requests()).isEmpty()
          harness.startRemoteResume(
            remoteUrl = remoteUrl,
            threadId = threadId,
            extraConfigArgs = listOf(CODEX_TERMINAL_TITLE_CONFIG),
          ).use { tui ->
            assertThat(tui.awaitTerminalThreadId()).isEqualTo(threadId.lowercase())
            assertThat(harness.requests()).isEmpty()
            val collaborationMode = CodexTurnCollaborationMode(
              mode = CODEX_PLAN_COLLABORATION_MODE,
              model = session.model,
              reasoningEffort = CODEX_DEFAULT_PLAN_REASONING_EFFORT,
              developerInstructions = null,
            )
            client.updateThreadCollaborationMode(
              threadId = threadId,
              collaborationMode = collaborationMode,
            )
            tui.awaitOutputContains("Plan mode")
            client.startTurn(
              threadId = threadId,
              text = prompt,
              collaborationMode = collaborationMode,
            )
            val planRequest = eventually(timeout = 20.seconds) {
              harness.requests()
                .takeIf { requests -> requests.size == 1 }
                ?.singleOrNull { request -> request.contains(prompt) }
            } ?: error("Timed out waiting for Codex app-server to send the Plan prompt request.\n${tui.diagnostics()}")
            assertThat(planRequest).doesNotContain("\"/plan\"")
            assertThat(planRequest).contains("Plan Mode")
            assertThat(planRequest).contains("<proposed_plan>")
            assertNativePlanImplementationPrompt(tui)
          }
        }
        finally {
          client.shutdown()
        }
      }
    }
  }

  @Test
  fun globalPromptPlanModeUsesRealCodexAppServerProviderDispatch() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      val prompt = "Plan this real global prompt integration test."
      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("global-prompt-plan-remote-resume"),
        responsePlans = listOf(
          MockResponsesPlan.completedAssistantMessage(PROPOSED_PLAN_RESPONSE),
        ),
      ).use { harness ->
        val client = CodexWebSocketAppServerClient(
          coroutineScope = this,
          executablePathProvider = { codexBinary },
          environmentOverrides = mapOf("CODEX_HOME" to harness.codexHome.toString()),
          workingDirectory = harness.projectDir,
        )
        val descriptor = realCodexDescriptor(codexBinary, RealCodexPlanPromptStartupBackend(client))
        try {
          val request = captureNewTaskPromptLaunchRequest(
            descriptor = descriptor,
            prompt = prompt,
            workingProjectPath = harness.projectDir.toString(),
          )
          assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
          assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
          assertThat(request.targetThreadId).isNull()

          val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)
          val launchSpec = descriptor.buildNewSessionLaunchSpec(request.launchMode)
          val prestartedPromptLaunch = descriptor.prestartNewSessionPromptLaunch(
            projectPath = request.projectPath,
            launchMode = request.launchMode,
            initialMessagePlan = initialMessagePlan,
            generationSettings = request.generationSettings,
            generationModelCatalog = request.generationModelCatalog,
            launchSpec = launchSpec,
          ) ?: error("Codex Plan-mode global prompt did not prestart a remote resume thread")

          val promptRecord = checkNotNull(prestartedPromptLaunch.initialMessageDispatchPlan.promptRecord)
          assertThat(promptRecord.message).isEqualTo(prompt)
          assertThat(promptRecord.mode).isEqualTo(AgentInitialMessageMode.PLAN)
          assertThat(promptRecord.deliveryStatus).isEqualTo(AgentInitialPromptDeliveryStatus.PENDING)
          assertThat(promptRecord.deliveryChannel).isEqualTo(AgentInitialPromptDeliveryChannel.APP_SERVER)
          val dispatchStep = checkNotNull(prestartedPromptLaunch.initialMessageDispatchPlan.terminalDispatch).steps.single()
          assertThat(dispatchStep.action).isEqualTo(AgentInitialMessageDispatchAction.PROVIDER)
          assertThat(dispatchStep.text).isEqualTo(prompt)
          assertThat(prestartedPromptLaunch.launchSpec.command).doesNotContain("/plan")
          assertThat(prestartedPromptLaunch.launchSpec.command).doesNotContain(prompt)
          assertThat(harness.requests()).isEmpty()

          val remoteResumeTarget = remoteResumeTarget(prestartedPromptLaunch.launchSpec.command)
          assertThat(prestartedPromptLaunch.launchSpec.preallocatedSessionId).isEqualTo(remoteResumeTarget.threadId)
          harness.startRemoteResume(
            remoteUrl = remoteResumeTarget.remoteUrl,
            threadId = remoteResumeTarget.threadId,
            extraConfigArgs = listOf(CODEX_TERMINAL_TITLE_CONFIG),
          ).use { tui ->
            assertThat(tui.awaitTerminalThreadId()).isEqualTo(remoteResumeTarget.threadId.lowercase())
            assertThat(harness.requests()).isEmpty()
            assertThat(descriptor.dispatchInitialMessageToProvider(
              AgentInitialMessageProviderDispatchRequest(
                project = ProjectManager.getInstance().defaultProject,
                projectPath = request.projectPath,
                threadId = remoteResumeTarget.threadId,
                message = dispatchStep.text,
                mode = promptRecord.mode,
                generationSettings = request.generationSettings,
              )
            )).isTrue()
            val planRequest = eventually(timeout = 20.seconds) {
              harness.requests()
                .takeIf { requests -> requests.size == 1 }
                ?.singleOrNull { modelRequest -> modelRequest.contains(prompt) }
            } ?: error("Timed out waiting for Codex global prompt Plan request.\n${tui.diagnostics()}")
            assertThat(planRequest).doesNotContain("\"/plan\"")
            assertThat(planRequest).contains("Plan Mode")
            assertThat(planRequest).contains("<proposed_plan>")
            assertNativePlanImplementationPrompt(tui)
          }
        }
        finally {
          client.shutdown()
        }
      }
    }
  }

  private fun requireRealCodexBinary(): String {
    assumeTrue(CodexRealTuiHarness.isSupportedPlatform(), "Real Codex app-server test is supported on macOS/Linux only.")
    val codexBinary = CodexRealTuiHarness.resolveCodexBinary()
    assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")
    return codexBinary!!
  }

  private suspend fun assertNativePlanImplementationPrompt(tui: RunningCodexTuiSession) {
    tui.awaitOutputContains("Plan mode")
    tui.awaitOutputContains("Proposed Plan")
    tui.awaitOutputContains("Implement this plan?")
    tui.awaitOutputContains("Yes, implement this plan")
    tui.awaitOutputContains("No, stay in Plan mode")
  }
}

private fun realCodexDescriptor(codexBinary: String, planPromptStartupBackend: CodexPlanPromptStartupBackend): AgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("codex")),
    planPromptStartupBackend = planPromptStartupBackend,
    executableResolver = { codexBinary },
    cliAvailableProbe = { true },
  ).withProvider(CODEX_AGENT_SESSION_PROVIDER)
}

private class RealCodexPlanPromptStartupBackend(
  private val client: CodexWebSocketAppServerClient,
) : CodexPlanPromptStartupBackend {
  private val prestartedPlanPromptModels: MutableMap<String, String> = ConcurrentHashMap()

  override suspend fun prestartPlanPromptThread(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    model: String?,
  ): CodexPrestartedPlanPrompt {
    val session = if (launchMode == AgentSessionLaunchMode.YOLO) {
      client.createThreadSession(cwd = projectPath, model = model, approvalPolicy = "on-request", sandbox = "workspace-write")
    }
    else {
      client.createThreadSession(cwd = projectPath, model = model)
    }
    val threadId = session.thread.id
    try {
      prestartedPlanPromptModels[threadId] = session.model
      client.materializeThread(threadId)
      return CodexPrestartedPlanPrompt(
        threadId = threadId,
        remoteUrl = client.currentRemoteUrl(),
      )
    }
    catch (t: Throwable) {
      prestartedPlanPromptModels.remove(threadId)
      runCatching { client.archiveThread(threadId) }
      throw t
    }
  }

  override suspend fun startPlanPromptTurn(
    threadId: String,
    prompt: String,
    model: String?,
    reasoningEffort: String?,
  ) {
    val effectiveModel = model ?: prestartedPlanPromptModels[threadId]
    val collaborationMode = CodexTurnCollaborationMode(
      mode = CODEX_PLAN_COLLABORATION_MODE,
      model = effectiveModel,
      reasoningEffort = reasoningEffort ?: CODEX_DEFAULT_PLAN_REASONING_EFFORT,
      developerInstructions = null,
    )
    client.updateThreadCollaborationMode(
      threadId = threadId,
      collaborationMode = collaborationMode,
    )
    client.startTurn(
      threadId = threadId,
      text = prompt,
      collaborationMode = collaborationMode,
    )
    prestartedPlanPromptModels.remove(threadId)
  }
}

private fun remoteResumeTarget(command: List<String>): RemoteResumeTarget {
  val remoteFlagIndex = command.indexOf("--remote")
  require(remoteFlagIndex >= 0 && remoteFlagIndex + 2 < command.size) {
    "Expected Codex remote resume command, got: $command"
  }
  return RemoteResumeTarget(
    remoteUrl = command[remoteFlagIndex + 1],
    threadId = command[remoteFlagIndex + 2],
  )
}

private data class RemoteResumeTarget(
  @JvmField val remoteUrl: String,
  @JvmField val threadId: String,
)

private const val CODEX_PLAN_COLLABORATION_MODE: String = "plan"
private const val CODEX_TERMINAL_TITLE_CONFIG: String = "tui.terminal_title=[\"thread-id\",\"thread\"]"
private const val CODEX_DEFAULT_PLAN_REASONING_EFFORT: String = "medium"
private const val PROPOSED_PLAN_RESPONSE: String = """<proposed_plan>
- Keep the implementation scoped to the requested integration path.
- Verify the real Codex TUI Plan prompt appears.
</proposed_plan>"""
