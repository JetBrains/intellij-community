// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPaletteSessionControllerTest {
  @BeforeEach
  fun clearPromptState() {
    val project = ProjectManager.getInstance().defaultProject
    project.service<AgentPromptUiSessionStateService>().clearDraft()
    project.service<AgentPromptUiSessionStateService>().loadState(AgentPromptUiState())
    project.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
  }

  @Test
  fun existingTaskTabKeepsProviderSelectorVisibleButHidesGenerationControls() {
    runInEdtAndWait {
      val fixture = createSessionControllerFixture()
      try {
        fixture.controller.initialize()
        fixture.controller.installHandlers()

        fixture.view.tabbedPane.selectedIndex = existingTaskTabIndex(fixture.view)
        layoutPopupRoot(fixture.view.rootPanel)

        assertThat(fixture.view.launchProfileLink.isVisible).isTrue()
        assertThat(fixture.view.profileAction.customComponent.isVisible).isTrue()
        assertThat(fixture.view.generationSettingsPanel.isVisible).isFalse()
        assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
        assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
      }
      finally {
        fixture.dispose()
      }
    }
  }

  @Test
  fun providerChangeOnExistingTaskTabReloadsTasksForSelectedProvider() {
    runInEdtAndWait {
      val fixture = createSessionControllerFixture()
      try {
        fixture.controller.initialize()
        fixture.controller.installHandlers()
        fixture.view.tabbedPane.selectedIndex = existingTaskTabIndex(fixture.view)

        assertThat(existingTaskIds(fixture.view)).containsExactly("codex-thread")
        fixture.view.existingTaskList.selectedIndex = 0
        assertThat(fixture.existingTaskController.selectedExistingTaskId).isEqualTo("codex-thread")

        fixture.providerSelector.selectProvider(AgentSessionProvider.CLAUDE)

        assertThat(fixture.launcher.observedProviders).containsExactly(AgentSessionProvider.CODEX, AgentSessionProvider.CLAUDE)
        assertThat(existingTaskIds(fixture.view)).containsExactly("claude-thread")
        assertThat(fixture.existingTaskController.selectedExistingTaskId).isEqualTo("claude-thread")
      }
      finally {
        fixture.dispose()
      }
    }
  }

  private fun createSessionControllerFixture(): SessionControllerFixture {
    val project = ProjectManager.getInstance().defaultProject
    val providers = listOf(
      testProviderDescriptor(AgentSessionProvider.CODEX),
      testProviderDescriptor(AgentSessionProvider.CLAUDE),
      testProviderDescriptor(AgentSessionProvider.JUNIE),
    )
    providers.forEach { provider ->
      service<AgentSessionProviderSettingsService>().setProviderEnabled(provider.provider, true)
    }
    project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      providers.associate { provider -> provider.provider to true }
    )

    val disposable = Disposer.newDisposable()

    @Suppress("RAW_SCOPE_CREATION")
    val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val launcher = RecordingPromptLauncher()
    val promptArea = AgentPromptTextField(project).apply {
      setDisposedWith(disposable)
    }
    lateinit var controllerRef: AgentPromptPaletteSessionController
    val suggestions = AgentPromptSuggestionsComponent { candidate -> controllerRef.applySuggestedPrompt(candidate) }
    val contextChips = AgentPromptContextChipsComponent { entry -> controllerRef.removeContextEntry(entry) }
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestions.component,
      contextChipsPanel = contextChips.component,
      onExistingTaskSelected = { selected -> controllerRef.onExistingTaskSelected(selected) },
    )
    val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteSessionControllerTest::class.java.classLoader)
    val invocationData = testInvocationData(project)
    val providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      headerControls = view.headerControls,
      providersProvider = { providers },
      sessionsMessageResolver = sessionsMessageResolver,
      onProviderOptionsChanged = { controllerRef.onProviderOptionsChanged() },
      onProviderSelectionChanged = { controllerRef.onProviderSelectionChanged() },
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = view.existingTaskListModel,
      existingTaskList = view.existingTaskList,
      popupScope = popupScope,
      sessionsMessageResolver = sessionsMessageResolver,
      onStateChanged = { controllerRef.onExistingTaskStateChanged() },
    )
    val suggestionController = AgentPromptSuggestionController(
      popupScope = popupScope,
      onSuggestionsUpdated = suggestions::render,
    )
    val controller = AgentPromptPaletteSessionController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      view = view,
      contextChips = contextChips,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      suggestionController = suggestionController,
      contextResolverService = project.service<AgentPromptContextResolverService>(),
      uiStateService = project.service<AgentPromptUiSessionStateService>(),
      launcherProvider = { launcher },
      closePopup = {},
      isPopupActive = { true },
      movePopupToFitScreen = {},
      popupScope = popupScope,
      parentDisposable = disposable,
    )
    controllerRef = controller
    return SessionControllerFixture(
      controller = controller,
      promptArea = promptArea,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      view = view,
      launcher = launcher,
      popupScope = popupScope,
      disposable = disposable,
    )
  }

  private fun existingTaskTabIndex(view: AgentPromptPaletteView): Int {
    for (index in 0 until view.tabbedPane.tabCount) {
      val component = view.tabbedPane.getComponentAt(index) as? JComponent ?: continue
      if (component.getClientProperty("targetMode") == PromptTargetMode.EXISTING_TASK) {
        return index
      }
    }
    error("Existing task tab not found")
  }

  private fun existingTaskIds(view: AgentPromptPaletteView): List<String> {
    return (0 until view.existingTaskListModel.size()).map { index -> view.existingTaskListModel.getElementAt(index).id }
  }

  private fun testInvocationData(project: Project): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
    )
  }

  private fun testProviderDescriptor(provider: AgentSessionProvider): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = "toolwindow.action.new.session.${provider.value}"
      override val icon = EmptyIcon.ICON_16
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey

      override suspend fun isCliAvailable(): Boolean = true

      override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return AgentInitialMessagePlan.EMPTY
      }
    }
  }

  private class RecordingPromptLauncher : AgentPromptLauncherBridge {
    val observedProviders = mutableListOf<AgentSessionProvider>()

    override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
      return AgentPromptLaunchResult.SUCCESS
    }

    override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String {
      return "/project"
    }

    override fun observeExistingThreads(projectPath: String, provider: AgentSessionProvider): Flow<AgentPromptExistingThreadsSnapshot> {
      observedProviders.add(provider)
      return flowOf(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(thread(provider)),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )
    }

    private fun thread(provider: AgentSessionProvider): AgentSessionThread {
      return AgentSessionThread(
        id = "${provider.value}-thread",
        title = "${provider.value} thread",
        updatedAt = 100,
        archived = false,
        activity = AgentThreadActivity.READY,
        provider = provider,
      )
    }
  }

  private data class SessionControllerFixture(
    @JvmField val controller: AgentPromptPaletteSessionController,
    @JvmField val promptArea: AgentPromptTextField,
    @JvmField val providerSelector: AgentPromptProviderSelector,
    @JvmField val existingTaskController: AgentPromptExistingTaskController,
    @JvmField val view: AgentPromptPaletteView,
    @JvmField val launcher: RecordingPromptLauncher,
    @JvmField val popupScope: CoroutineScope,
    @JvmField val disposable: com.intellij.openapi.Disposable,
  ) {
    fun dispose() {
      controller.onPopupClosed()
      popupScope.cancel()
      Disposer.dispose(disposable)
    }
  }
}
