// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteInitialPrompt
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ai.agent.common.session.isClaudeMenuCommandPrompt
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPromptProviderOption
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPaletteSubmitControllerTest {
  @Test
  fun captureNewTaskPromptLaunchRequestSeedsProviderStateBeforeSubmit() {
    val project = ProjectManager.getInstance().defaultProject
    val providerSettings = service<AgentSessionProviderSettingsService>()
    val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
    providerSettings.setProviderEnabled(AgentSessionProvider.from("codex"), false)
    availabilityService.setAvailabilityForTest(mapOf(AgentSessionProvider.from("codex") to false))
    try {
      val request = captureNewTaskPromptLaunchRequest(
        descriptor = testProviderBridge(
          provider = AgentSessionProvider.from("codex"),
          promptOptions = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION),
        ),
        prompt = "Plan this refactor",
        workingProjectPath = "/repo",
        project = project,
      )

      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(request.projectPath).isEqualTo("/repo")
      assertThat(request.initialMessageRequest.prompt).isEqualTo("Plan this refactor")
      assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(request.targetThreadId).isNull()
    }
    finally {
      providerSettings.setProviderEnabled(AgentSessionProvider.from("codex"), true)
      availabilityService.clearAvailabilityForTest()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun successfulSubmitDisposesEditorTextFieldWithModelAccessFromUiDispatcher(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val provider = AgentSessionProvider.from("codex")
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)
    val editorDisposable = Disposer.newCheckedDisposable("Agent prompt submit success disposal test")
    val disposed = CompletableDeferred<Unit>()
    service<AgentSessionProviderSettingsService>().setProviderEnabled(provider, true)
    project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(mapOf(provider to true))
    try {
      val fixture = withContext(Dispatchers.UiWithModelAccess) {
        val fixture = createFixture(
          project = project,
          sessionScope = sessionScope,
          launcherProvider = {
            object : AgentPromptLauncherBridge {
              override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
                return AgentPromptLaunchResult.SUCCESS
              }

              override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/repo"
            }
          },
          currentTargetMode = { PromptTargetMode.NEW_TASK },
          onSubmitSucceeded = {
            try {
              Disposer.dispose(editorDisposable)
              disposed.complete(Unit)
            }
            catch (error: Throwable) {
              disposed.completeExceptionally(error)
              throw error
            }
          },
        )
        fixture.providerSelector.refresh()
        fixture.providerSelector.selectProvider(provider)
        fixture.promptArea.setDisposedWith(editorDisposable)
        fixture.promptArea.getEditor(true)
        fixture.promptArea.text = "Refactor selected code"
        fixture.launchState.selectedWorkingProjectPath = "/repo"
        fixture
      }

      withContext(Dispatchers.UI) {
        fixture.controller.submit()
      }
      disposed.await()
    }
    finally {
      sessionScope.cancel()
      project.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
      if (!editorDisposable.isDisposed) {
        withContext(Dispatchers.UiWithModelAccess) {
          Disposer.dispose(editorDisposable)
        }
      }
    }
  }

  @Test
  fun resolveWorkingProjectPathPrefersUserSelectedPathOverLauncherPath() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest) =
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
  fun updateSendAvailabilityUsesNormalLaunchPrerequisitesForExtensionWithoutSubmitAction() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val fixture = createFixture(
        project = project,
        activeExtensionTab = { extensionTabWithoutSubmitAction() },
        currentTargetMode = { PromptTargetMode.EXISTING_TASK },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Review selected changes"

      fixture.controller.updateSendAvailability()
      assertThat(fixture.controller.canSubmit()).isFalse()

      fixture.launchState.selectedWorkingProjectPath = "/repo"
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
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("claude"))) },
        buildVisibleContextEntries = { listOf(ContextEntry(contextItem)) },
        resolveContextSelection = { _, _ ->
          resolveContextSelectionCalls += 1
          AgentPromptPaletteContextSelection(listOf(contextItem), AgentPromptContextEnvelopeSummary())
        },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("claude"))
      fixture.promptArea.text = "/mcp"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.selectedExistingTaskId = "thread-1"

      fixture.controller.submit()

      assertThat(resolveContextSelectionCalls).isZero()
      assertThat(capturedRequest).isNotNull
      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("claude"))
      assertThat(request.initialMessageRequest.prompt).isEqualTo("/mcp")
      assertThat(request.initialMessageRequest.contextItems).isEmpty()
      assertThat(request.initialMessageRequest.contextEnvelopeSummary).isNull()
    }
  }

  @Test
  fun submitIncludesLaunchProfileId() {
    runInEdtAndWait {
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = ProjectManager.getInstance().defaultProject,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        launchProfileIdProvider = { "profile:codex-fast" },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Plan the change"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      assertThat(checkNotNull(capturedRequest).launchProfileId).isEqualTo("profile:codex-fast")
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
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = {
          listOf(
            testProviderBridge(
              provider = AgentSessionProvider.from("codex"),
              promptOptions = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION),
            )
          )
        },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Plan this refactor"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(request.initialMessageRequest.prompt).isEqualTo("Plan this refactor")
      assertThat(request.initialMessageRequest.providerOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(request.targetThreadId).isNull()
    }
  }

  @Test
  fun submitIncludesGenerationSettingsForNewTaskLaunch() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        generationSettingsProvider = {
          AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.MEDIUM)
        },
        generationModelCatalogProvider = {
          listOf(
            AgentPromptGenerationModel(id = "gpt-5", displayName = "GPT-5"),
            AgentPromptGenerationModel(id = "claude-sonnet-4-5", displayName = "Claude Sonnet 4.5"),
          )
        },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.generationSettings.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.MEDIUM)
      assertThat(request.generationModelCatalog.map { model -> model.id }).containsExactly("gpt-5", "claude-sonnet-4-5")
      assertThat(request.targetThreadId).isNull()
    }
  }

  @Test
  fun submitUsesNormalNewTaskLaunchForExtensionWithoutSubmitAction() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
            object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.EXISTING_TASK },
        activeExtensionTab = { extensionTabWithoutSubmitAction() },
        generationSettingsProvider = {
          AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH)
        },
        generationModelCatalogProvider = {
          listOf(AgentPromptGenerationModel(id = "qwen3.6", displayName = "Qwen 3.6"))
        },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Review selected changes"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.selectedExistingTaskId = "thread-1"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(request.projectPath).isEqualTo("/repo")
      assertThat(request.initialMessageRequest.prompt).isEqualTo("Review selected changes")
      assertThat(request.targetThreadId).isNull()
      assertThat(request.generationSettings.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
      assertThat(request.generationModelCatalog.map { model -> model.id }).containsExactly("qwen3.6")
    }
  }

  @Test
  fun submitIgnoresGenerationSettingsForExistingTaskLaunch() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.EXISTING_TASK },
        generationSettingsProvider = {
          AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH)
        },
        generationModelCatalogProvider = {
          listOf(AgentPromptGenerationModel(id = "gpt-5", displayName = "GPT-5"))
        },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.selectedExistingTaskId = "thread-1"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.generationSettings).isEqualTo(AgentPromptGenerationSettings.AUTO)
      assertThat(request.generationModelCatalog).isEmpty()
      assertThat(request.targetThreadId).isEqualTo("thread-1")
    }
  }

  @Test
  fun submitKeepsContainerModeForSupportedProvider() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("claude"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        isContainerModeSelected = { true },
        isContainerModeSupported = { provider -> provider == AgentSessionProvider.from("claude") },
        isContainerModeRuntimeAvailable = { provider -> provider == AgentSessionProvider.from("claude") },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("claude"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("claude"))
      assertThat(request.containerMode).isTrue()
    }
  }

  @Test
  fun submitClearsPersistedContainerModeForUnsupportedProvider() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        isContainerModeSelected = { true },
        isContainerModeSupported = { provider -> provider == AgentSessionProvider.from("claude") },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(request.containerMode).isFalse()
    }
  }

  @Test
  fun submitClearsPersistedContainerModeWhenRuntimeIsUnavailable() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("claude"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        isContainerModeSelected = { true },
        isContainerModeSupported = { provider -> provider == AgentSessionProvider.from("claude") },
        isContainerModeRuntimeAvailable = { false },
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("claude"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      val request = checkNotNull(capturedRequest)
      assertThat(request.provider).isEqualTo(AgentSessionProvider.from("claude"))
      assertThat(request.containerMode).isFalse()
    }
  }

  @Test
  fun submitRecordsPromptHistoryOnlyAfterSuccessfulLaunch() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val submittedHistory = mutableListOf<AgentPromptHistoryEntry>()
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        onPromptSubmitted = submittedHistory::add,
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "  Refactor selected code  "
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      assertThat(submittedHistory).hasSize(1)
      assertThat(submittedHistory.single().promptText).isEqualTo("Refactor selected code")
      assertThat(submittedHistory.single().providerId).isEqualTo("codex")
      assertThat(submittedHistory.single().targetMode).isEqualTo(PromptTargetMode.NEW_TASK)
      assertThat(submittedHistory.single().launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD.name)
    }
  }

  @Test
  fun failedSubmitDoesNotRecordPromptHistory() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val submittedHistory = mutableListOf<AgentPromptHistoryEntry>()
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              return AgentPromptLaunchResult.failure(AgentPromptLaunchError.INTERNAL_ERROR)
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = { listOf(testProviderBridge(provider = AgentSessionProvider.from("codex"))) },
        currentTargetMode = { PromptTargetMode.NEW_TASK },
        onPromptSubmitted = submittedHistory::add,
      )
      fixture.providerSelector.refresh()
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.promptArea.text = "Refactor selected code"
      fixture.launchState.selectedWorkingProjectPath = "/repo"

      fixture.controller.submit()

      assertThat(submittedHistory).isEmpty()
    }
  }

  @Test
  fun submitBlocksPlanOptionForBusyExistingTask() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      var capturedRequest: AgentPromptLaunchRequest? = null
      var blockedMessage: String? = null
      val fixture = createFixture(
        project = project,
        launcherProvider = {
          object : AgentPromptLauncherBridge {
            override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
              capturedRequest = request
              return AgentPromptLaunchResult.SUCCESS
            }

            override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = "/launcher/path"
          }
        },
        providersProvider = {
          listOf(
            testProviderBridge(
              provider = AgentSessionProvider.from("codex"),
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
      fixture.providerSelector.selectProvider(AgentSessionProvider.from("codex"))
      fixture.providerSelector.restoreProviderOptionSelections(
        mapOf(AgentSessionProvider.from("codex").value to setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE))
      )
      fixture.promptArea.text = "Investigate the flaky test"
      fixture.launchState.selectedWorkingProjectPath = "/repo"
      fixture.existingTaskController.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Busy Thread",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.from("codex"),
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
    sessionScope: CoroutineScope = testScope(),
    launcherProvider: () -> AgentPromptLauncherBridge? = { null },
    providersProvider: () -> List<AgentSessionProviderDescriptor> = { listOf(testProviderBridge()) },
    buildVisibleContextEntries: () -> List<ContextEntry> = { emptyList() },
    resolveContextSelection: (List<AgentPromptContextItem>, String?) -> AgentPromptPaletteContextSelection? = { _, _ ->
      AgentPromptPaletteContextSelection(emptyList(), AgentPromptContextEnvelopeSummary())
    },
    currentTargetMode: () -> PromptTargetMode = { PromptTargetMode.EXISTING_TASK },
    onSubmitBlocked: (String) -> Unit = {},
    onSubmitSucceeded: () -> Unit = {},
    onPromptSubmitted: (AgentPromptHistoryEntry) -> Unit = {},
    launchProfileIdProvider: () -> String? = { null },
    generationSettingsProvider: () -> AgentPromptGenerationSettings = { AgentPromptGenerationSettings.AUTO },
    generationModelCatalogProvider: () -> List<AgentPromptGenerationModel> = { emptyList() },
    isContainerModeSelected: () -> Boolean = { false },
    isContainerModeSupported: (AgentSessionProvider) -> Boolean = { false },
    isContainerModeRuntimeAvailable: (AgentSessionProvider) -> Boolean = { false },
    activeExtensionTab: () -> AgentPromptPaletteExtensionTab? = { null },
  ): SubmitControllerFixture {
    val promptArea = EditorTextField()
    val view = createAgentPromptPaletteView(
      promptArea = EditorTextField(),
      contextChipsPanel = JPanel(),
      onExistingTaskSelected = {},
    )
    val providerSelector = AgentPromptProviderSelector(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
        actionText = "Ask Agent",
        actionPlace = "MainMenu",
        invokedAtMs = 0L,
      ),
      headerControls = view.headerControls,
      providersProvider = providersProvider,
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteSubmitControllerTest::class.java.classLoader),
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = javax.swing.DefaultListModel(),
      existingTaskList = com.intellij.ui.components.JBList(),
      sessionScope = testScope(),
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
      sessionScope = sessionScope,
      launcherProvider = launcherProvider,
      launchState = launchState,
      currentTargetMode = currentTargetMode,
      activeExtensionTab = activeExtensionTab,
      buildVisibleContextEntries = buildVisibleContextEntries,
      resolveContextSelection = resolveContextSelection,
      onWorkingProjectPathSelected = {},
      onSubmitBlocked = onSubmitBlocked,
      onSubmitSucceeded = onSubmitSucceeded,
      onPromptSubmitted = onPromptSubmitted,
      launchProfileIdProvider = launchProfileIdProvider,
      generationSettingsProvider = generationSettingsProvider,
      generationModelCatalogProvider = generationModelCatalogProvider,
      isContainerModeSelected = isContainerModeSelected,
      isContainerModeSupported = isContainerModeSupported,
      isContainerModeRuntimeAvailable = isContainerModeRuntimeAvailable,
    )
    return SubmitControllerFixture(controller, promptArea, providerSelector, existingTaskController, launchState)
  }

  private fun testProviderBridge(
    provider: AgentSessionProvider = AgentSessionProvider.from("codex"),
    promptOptions: List<AgentPromptProviderOption> = emptyList(),
    initialMessagePlanBuilder: (AgentPromptInitialMessageRequest) -> AgentInitialMessagePlan = { AgentInitialMessagePlan.EMPTY },
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val displayNameKey: String = if (provider == AgentSessionProvider.from("claude")) "provider.claude" else "provider.codex"
      override val newSessionLabelKey: String = displayNameKey
      override val promptOptions: List<AgentPromptProviderOption> = promptOptions
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16

      override suspend fun isCliAvailable(): Boolean = true

      override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return initialMessagePlanBuilder(request)
      }

      override fun shouldStripContextForPrompt(prompt: String): Boolean {
        return this.provider == AgentSessionProvider.from("claude") && prompt.isClaudeMenuCommandPrompt()
      }
    }
  }

  private fun extensionTabWithoutSubmitAction(): AgentPromptPaletteExtensionTab {
    return AgentPromptPaletteExtensionTab(
      extension = object : AgentPromptPaletteExtension {
        override fun matches(contextItems: List<AgentPromptContextItem>): Boolean = true

        override fun getTabTitle(): String = "Review"

        override fun getInitialPrompt(project: com.intellij.openapi.project.Project): AgentPromptPaletteInitialPrompt =
          AgentPromptPaletteInitialPrompt(content = "Review selected changes")

        override fun getSubmitActionId(): String? = null

        override fun getFooterHint(): String? = null
      },
      tabPanel = JPanel(),
      taskKeyPrefix = "review",
    )
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
