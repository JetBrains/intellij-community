// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.chat.RecordingAgentChatTerminalHarness
import com.intellij.agent.workbench.chat.RecordingTerminalSentText
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.ui.captureNewTaskPromptLaunchRequest
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.launchNewThreadPromptRequestWithDefaultChatOpenExecutor
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
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
  fun globalPromptNewTaskPlanModeStartsCodexThenDispatchesPromptInTerminal(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val descriptor = descriptor()
      val terminalHarness = RecordingAgentChatTerminalHarness()
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

      assertThat(request.provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(request.projectPath).isEqualTo(projectPath)
      assertThat(request.initialMessageRequest.prompt).isEqualTo(PLAN_PROMPT)
      assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(request.targetThreadId).isNull()

      launchNewThreadPromptRequestWithDefaultChatOpenExecutor(
        descriptor = descriptor,
        request = request,
        openedChatHandler = { openedProject, openedFile ->
          check(terminalHarness.activateAgentChatEditor(project = openedProject, file = openedFile) == 1)
        },
      ) {
        terminalHarness.awaitCreateCalls(1)
        terminalHarness.setRunning()
        terminalHarness.awaitBackTabCalls(1)
        terminalHarness.awaitSentTexts(1)
        val finalSnapshot = terminalHarness.awaitInitialMessageSent()
        assertThat(finalSnapshot.initialMessageDispatchStepIndex).isEqualTo(2)
      }

      val startupCommand = terminalHarness.startupLaunchSpecs.single().command
      assertThat(startupCommand).containsExactlyElementsOf(CODEX_BASE_COMMAND)
      assertThat(startupCommand).doesNotContain("--")
      assertThat(startupCommand).doesNotContain(PLAN_PROMPT)
      assertThat(terminalHarness.backTabCalls).isEqualTo(1)
      assertThat(terminalHarness.sentTexts)
        .containsExactly(RecordingTerminalSentText(PLAN_PROMPT, shouldExecute = true, useBracketedPasteMode = true))
    }
}

private suspend fun <T> runInUi(action: suspend () -> T): T {
  return withContext(Dispatchers.UiWithModelAccess) {
    action()
  }
}

private fun descriptor(): CodexAgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.CODEX),
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
  )
}

private const val PLAN_PROMPT: String = "Plan this refactor"

private val CODEX_BASE_COMMAND: List<String> = listOf(
  "codex",
  "-c",
  "check_for_update_on_startup=false",
  "-c",
  "tui.terminal_title=[\"thread-id\",\"thread\"]",
)
