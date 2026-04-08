// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class AgentPromptPaletteSubmitControllerTest {
  @Test
  fun resolveWorkingProjectPathPrefersUserSelectedPathOverLauncherPath() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override fun launch(request: AgentPromptLaunchRequest) =
              AgentPromptLaunchResult.SUCCESS

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
      )
      fixture.launchState.selectedWorkingProjectPath = "/selected/path"

      assertThat(fixture.controller.resolveWorkingProjectPath()).isEqualTo("/selected/path")
    }
  }

  @Test
  fun updateSendAvailabilityRequiresExistingTaskSelectionForExistingTaskMode() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val fixture = createFixture(project = project)
      fixture.promptArea.text = "prompt"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.providerSelector.refresh()
      fixture.controller.updateSendAvailability()
      assertThat(fixture.controller.canSubmit()).isFalse()

      fixture.existingTaskController.selectedExistingTaskId = "thread-1"
      fixture.controller.updateSendAvailability()
      assertThat(fixture.controller.canSubmit()).isTrue()
    }
  }

  @Test
  fun submitStripsContextForClaudeMenuPrompt() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      var resolveContextSelectionCalls = 0
      val contextItem = AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.PATHS,
        title = "Project Selection",
        body = "file: /tmp/demo.kt",
        source = "projectView",
      )
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.CLAUDE)) },
        buildVisibleContextEntries = { listOf(ContextEntry(contextItem)) },
        resolveContextSelection = { _, _ ->
          resolveContextSelectionCalls += 1
          AgentPromptPaletteContextSelection(listOf(contextItem), AgentPromptContextEnvelopeSummary())
        },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.CLAUDE)
      fixture.promptArea.text = "/mcp"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.selectedExistingTaskId = "thread-1"

      fixture.controller.submit()

      assertThat(resolveContextSelectionCalls).isZero()
      assertThat(capturedRequest).isNotNull
      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.CLAUDE)
      assertThat(request.initialMessageRequest.prompt).isEqualTo("/mcp")
      assertThat(request.initialMessageRequest.contextItems).isEmpty()
      assertThat(request.initialMessageRequest.contextEnvelopeSummary).isNull()
    }
  }

  @Test
  fun submitKeepsPlanModeEnabledForNewTaskCodexLaunch() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = {
          listOf(
            testProviderBridge(
              provider = AgentSessionProvider.CODEX,
              promptOptions = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION),
            )
          )
        },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.CODEX)
      fixture.promptArea.text = "Plan this refactor"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(request.initialMessageRequest.prompt).isEqualTo("Plan this refactor")
      assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(request.targetThreadId).isNull()
    }
  }

  @Test
  fun submitBlocksManualPlanPromptForBusyExistingTask() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      var blockedMessage: String? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = {
          listOf(
            testProviderBridge(
              provider = AgentSessionProvider.CODEX,
              promptOptions = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION),
              initialMessagePlanBuilder = { request ->
                buildPlanModeInitialMessagePlan(
                  request = request,
                  startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.POST_START_ONLY,
                )
              },
            )
          )
        },
        onSubmitBlocked = { blockedMessage = it },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.CODEX)
      fixture.promptArea.text = "/plan Investigate the flaky test"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Busy Thread",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.PROCESSING,
              subAgents = emptyList(),
            )
          ),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )
      fixture.existingTaskController.selectedExistingTaskId = "thread-1"

      fixture.controller.submit()

      assertThat(capturedRequest).isNull()
      assertThat(blockedMessage).isEqualTo(AgentPromptBundle.message("popup.error.existing.plan.busy"))
    }
  }

  private fun createFixture(
    project: com.intellij.openapi.project.Project,
    launcherProvider: () -> AgentPromptLauncherBridge? = { null },
    providersProvider: () -> List<AgentSessionProviderDescriptor> = { listOf(testProviderBridge()) },
    buildVisibleContextEntries: () -> List<ContextEntry> = { emptyList() },
    resolveContextSelection: (List<AgentPromptContextItem>, String?) -> AgentPromptPaletteContextSelection? = { _, _ ->
      AgentPromptPaletteContextSelection(emptyList(), AgentPromptContextEnvelopeSummary())
    },
    currentTargetMode: () -> PromptTargetMode = { PromptTargetMode.EXISTING_TASK },
    onSubmitBlocked: (String) -> Unit = {},
    onSubmitSucceeded: () -> Unit = {},
  ): SubmitControllerFixture {
    val promptArea = EditorTextField()
    val providerSelector = AgentPromptProviderSelector(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
        actionText = "Ask Agent",
        actionPlace = "MainMenu",
        invokedAtMs = 0L,
      ),
      providerIconLabel = JBLabel(),
      providerOptionsPanel = JPanel(),
      providersProvider = providersProvider,
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteSubmitControllerTest::class.java.classLoader),
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = javax.swing.DefaultListModel(),
      existingTaskList = com.intellij.ui.components.JBList(),
      popupScope = testScope(),
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteSubmitControllerTest::class.java.classLoader),
      onStateChanged = {},
    )
    val launchState = AgentPromptPaletteLaunchState()
    val controller = AgentPromptPaletteSubmitController(
      project = project,
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
        actionText = "Ask Agent",
        actionPlace = "MainMenu",
        invokedAtMs = 0L,
      ),
      promptArea = promptArea,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      launcherProvider = launcherProvider,
      launchState = launchState,
      currentTargetMode = currentTargetMode,
      activeExtensionTab = { null },
      buildVisibleContextEntries = buildVisibleContextEntries,
      resolveContextSelection = resolveContextSelection,
      onWorkingProjectPathSelected = {},
      onSubmitBlocked = onSubmitBlocked,
      onSubmitSucceeded = onSubmitSucceeded,
    )
    return SubmitControllerFixture(controller, promptArea, providerSelector, existingTaskController, launchState)
  }

  private fun testProviderBridge(
    provider: AgentSessionProvider = AgentSessionProvider.CODEX,
    promptOptions: List<AgentPromptProviderOption> = emptyList(),
    initialMessagePlanBuilder: (AgentPromptInitialMessageRequest) -> AgentInitialMessagePlan = { AgentInitialMessagePlan.EMPTY },
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val displayNameKey: String = if (provider == AgentSessionProvider.CLAUDE) "provider.claude" else "provider.codex"
      override val newSessionLabelKey: String = displayNameKey
      override val promptOptions: List<AgentPromptProviderOption> = promptOptions
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16

      override fun isCliAvailable(): Boolean = true

      override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return initialMessagePlanBuilder(request)
      }
    }
  }

  private data class SubmitControllerFixture(
    val controller: AgentPromptPaletteSubmitController,
    val promptArea: EditorTextField,
    val providerSelector: AgentPromptProviderSelector,
    val existingTaskController: AgentPromptExistingTaskController,
    val launchState: AgentPromptPaletteLaunchState,
  )

  @Suppress("RAW_SCOPE_CREATION")
  private fun testScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  }
}
