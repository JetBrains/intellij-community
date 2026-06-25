// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.agent.workbench.chat.RecordingAgentChatTerminalHarness
import com.intellij.agent.workbench.chat.disposeAgentChatLiveTerminalsForTest
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.launchNewThreadPromptRequestWithDefaultChatOpenExecutor
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.withProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexNewThreadPromptLaunchTerminalIntegrationTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project get() = projectFixture.get()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun globalPromptNewTaskPlanModeLaunchesRemoteResumeWithoutTerminalDispatch(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val startupBackend = RecordingPlanPromptStartupBackend()
      val descriptor = descriptor(startupBackend)
      val terminalHarness = RecordingAgentChatTerminalHarness()
      var openedChatActivated = false
      runInUi {
        fileEditorManagerFixture.get()
        terminalHarness.registerEditorFactory(disposable)
      }

      val projectPath = checkNotNull(project.basePath)
      val request = captureNewTaskPromptLaunchRequest(
        descriptor = descriptor,
        prompt = PLAN_PROMPT,
        workingProjectPath = projectPath,
        project = project,
      ).copy(preferredDedicatedFrame = false)

      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(request.projectPath).isEqualTo(projectPath)
      assertThat(request.initialMessageRequest.prompt).isEqualTo(PLAN_PROMPT)
      assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(request.targetThreadId).isNull()

      launchNewThreadPromptRequestWithDefaultChatOpenExecutor(
        descriptor = descriptor,
        request = request,
        openedChatHandler = { openedProject, openedFile ->
          check(terminalHarness.activateAgentChatEditor(project = openedProject, file = openedFile) == 1)
          openedChatActivated = true
        },
      ) {
        terminalHarness.awaitCreateCalls(1)
        terminalHarness.setRunning()
        terminalHarness.awaitInitialMessageSent()
        assertThat(terminalHarness.awaitSentTextsStayAt(0)).isEmpty()

        val startupCommand = terminalHarness.startupLaunchSpecs.single().command
        assertThat(startupCommand)
          .containsExactlyElementsOf(CODEX_BASE_COMMAND + listOf("resume", "--remote", REMOTE_URL, RECORDED_PLAN_THREAD_ID))
        assertThat(startupCommand).doesNotContain("--")
        assertThat(startupCommand).doesNotContain(PLAN_PROMPT)
        assertThat(terminalHarness.sentTexts).isEmpty()
        assertThat(startupBackend.turnRequests).containsExactly(
          CodexPlanPromptTurnRequest(
            threadId = RECORDED_PLAN_THREAD_ID,
            prompt = PLAN_PROMPT,
            model = null,
            reasoningEffort = null,
          )
        )
        // Close while the test Codex descriptor is still registered so terminal-close cleanup
        // does not route the fake preallocated UUID through the production app-server backend.
        check(openedChatActivated)
        disposeAgentChatLiveTerminalsForTest(project)
      }
    }
}

private suspend fun <T> runInUi(action: suspend () -> T): T {
  return withContext(Dispatchers.UiWithModelAccess) {
    action()
  }
}

private fun descriptor(planPromptStartupBackend: CodexPlanPromptStartupBackend): AgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("codex")),
    threadMutationBackend = NoopCodexThreadMutationBackend,
    planPromptStartupBackend = planPromptStartupBackend,
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
  ).withProvider(CODEX_AGENT_SESSION_PROVIDER)
}

private object NoopCodexThreadMutationBackend : CodexThreadMutationBackend {
  override suspend fun archiveThread(path: String, threadId: String) {
  }

  override suspend fun unarchiveThread(path: String, threadId: String) {
  }

  override suspend fun setThreadName(path: String, threadId: String, name: String) {
  }
}

private const val PLAN_PROMPT: String = "Plan this refactor"

private val CODEX_BASE_COMMAND: List<String> = listOf(
  "codex",
  "-c",
  "check_for_update_on_startup=false",
  "-c",
  "tui.terminal_title=[\"thread-id\",\"thread\"]",
)
