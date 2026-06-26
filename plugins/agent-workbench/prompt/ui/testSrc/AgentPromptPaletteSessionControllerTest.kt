// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiBackend
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPaletteSessionControllerTest {
  private val registeredExtensionPoints = mutableListOf<String>()

  @BeforeEach
  fun setUp() {
    ensurePromptExtensionPoints()
    val project = ProjectManager.getInstance().defaultProject
    project.service<AgentPromptUiSessionStateService>().clearDraft()
    project.service<AgentPromptUiSessionStateService>().loadState(AgentPromptUiState())
    project.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
  }

  @AfterEach
  fun tearDown() {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    for (extensionPointName in registeredExtensionPoints.asReversed()) {
      extensionArea.unregisterExtensionPoint(extensionPointName)
    }
    registeredExtensionPoints.clear()
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

        fixture.providerSelector.selectProvider(AgentSessionProvider.from("claude"))

        assertThat(fixture.launcher.observedProviders).containsExactly(AgentSessionProvider.from("codex"),
                                                                       AgentSessionProvider.from("claude"))
        assertThat(existingTaskIds(fixture.view)).containsExactly("claude-thread")
        assertThat(fixture.existingTaskController.selectedExistingTaskId).isEqualTo("claude-thread")
      }
      finally {
        fixture.dispose()
      }
    }
  }

  @Test
  fun closeWithDraftPersistsSelectedLaunchProfileForNextPopup() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val profile = carefulClaudeProfile()
      val preferences = AgentPromptLauncherBridge.ProviderPreferences(launchProfiles = listOf(profile))
      val firstPopup = createSessionControllerFixture(providerPreferences = preferences)
      try {
        firstPopup.controller.initialize()
        firstPopup.controller.installHandlers()
        assertThat(firstPopup.controller.applyLaunchProfileForTest(profile.id)).isTrue()
        firstPopup.promptArea.text = "draft"
      }
      finally {
        firstPopup.dispose()
      }

      val savedDraft = project.service<AgentPromptUiSessionStateService>().loadDraft()
      assertThat(savedDraft.selectedLaunchProfileId).isEqualTo(profile.id)

      val reopenedPopup = createSessionControllerFixture(providerPreferences = preferences)
      try {
        reopenedPopup.controller.initialize()

        assertThat(reopenedPopup.providerSelector.selectedProvider?.bridge?.provider).isEqualTo(AgentSessionProvider.from("claude"))
      }
      finally {
        reopenedPopup.dispose()
      }
    }
  }

  @Test
  fun closeWithoutDraftDoesNotPersistSelectedLaunchProfileForNextPopup() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val profile = carefulClaudeProfile()
      val preferences = AgentPromptLauncherBridge.ProviderPreferences(launchProfiles = listOf(profile))
      val firstPopup = createSessionControllerFixture(providerPreferences = preferences)
      try {
        firstPopup.controller.initialize()
        firstPopup.controller.installHandlers()
        assertThat(firstPopup.controller.applyLaunchProfileForTest(profile.id)).isTrue()
      }
      finally {
        firstPopup.dispose()
      }

      val savedDraft = project.service<AgentPromptUiSessionStateService>().loadDraft()
      assertThat(savedDraft.selectedLaunchProfileId).isNull()

      val reopenedPopup = createSessionControllerFixture(providerPreferences = preferences)
      try {
        reopenedPopup.controller.initialize()

        assertThat(reopenedPopup.providerSelector.selectedProvider?.bridge?.provider).isEqualTo(AgentSessionProvider.from("codex"))
      }
      finally {
        reopenedPopup.dispose()
      }
    }
  }

  @Test
  fun initializeIgnoresStaleDraftLaunchProfile() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      project.service<AgentPromptUiSessionStateService>().saveDraft(
        AgentPromptUiDraft(
          promptText = "draft",
          taskDrafts = mapOf(PromptTargetMode.NEW_TASK.name to "draft"),
          selectedLaunchProfileId = "user:missing",
        )
      )
      val fixture = createSessionControllerFixture()
      try {
        fixture.controller.initialize()

        assertThat(fixture.providerSelector.selectedProvider?.bridge?.provider).isEqualTo(AgentSessionProvider.from("codex"))
      }
      finally {
        fixture.dispose()
      }
    }
  }

  @Test
  fun inlineEmptyStateHidesDefaultFooterUntilStatusIsShown() {
    runInEdtAndWait {
      var revalidateCalls = 0
      val fixture = createSessionControllerFixture(
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
        revalidateHost = { revalidateCalls++ },
      )
      try {
        fixture.controller.initialize()

        assertThat(fixture.view.footerPanel.isVisible).isFalse()

        fixture.controller.applyAddContextRequest(
          AgentPromptAddContextRequest(
            contextItems = listOf(AgentPromptContextItem(rendererId = "test", title = "File", body = "src/Main.kt")),
            target = null,
          )
        )

        assertThat(fixture.view.footerPanel.isVisible).isTrue()
        assertThat(fixture.view.statusStrip.text).isEqualTo(AgentPromptBundle.message("popup.status.context.added"))
        assertThat(revalidateCalls).isGreaterThan(0)

        fixture.controller.initialize()

        assertThat(fixture.view.footerPanel.isVisible).isFalse()
      }
      finally {
        fixture.dispose()
      }
    }
  }

  private fun createSessionControllerFixture(
    providerPreferences: AgentPromptLauncherBridge.ProviderPreferences = AgentPromptLauncherBridge.ProviderPreferences(),
    hostMode: AgentPromptPaletteHostMode = AgentPromptPaletteHostMode.POPUP,
    revalidateHost: () -> Unit = {},
  ): SessionControllerFixture {
    val project = ProjectManager.getInstance().defaultProject
    val providers = listOf(
      testProviderDescriptor(AgentSessionProvider.from("codex")),
      testProviderDescriptor(AgentSessionProvider.from("claude")),
      testProviderDescriptor(AgentSessionProvider.from("junie")),
    )
    providers.forEach { provider ->
      service<AgentSessionProviderSettingsService>().setProviderEnabled(provider.provider, true)
    }
    project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      providers.associate { provider -> provider.provider to true }
    )

    @Suppress("RAW_SCOPE_CREATION")
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val launcher = RecordingPromptLauncher(providerPreferences)
    val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteSessionControllerTest::class.java.classLoader)
    val invocationData = testInvocationData(project)
    val content = createAgentPromptPaletteContent(
      invocationData = invocationData,
      contextResolverService = project.service<AgentPromptContextResolverService>(),
      uiStateService = project.service<AgentPromptUiSessionStateService>(),
      sessionsMessageResolver = sessionsMessageResolver,
      providersProvider = { providers },
      launcherProvider = { launcher },
      closeHost = {},
      isHostActive = { true },
      revalidateHost = revalidateHost,
      hostMode = hostMode,
      sessionScope = sessionScope,
    )
    return SessionControllerFixture(
      content = content,
      controller = content.sessionController,
      promptArea = content.promptArea,
      providerSelector = content.providerSelector,
      existingTaskController = content.existingTaskController,
      view = content.view,
      launcher = launcher,
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

  private fun carefulClaudeProfile(): AgentPromptLaunchProfile {
    return AgentPromptLaunchProfile(
      id = "user:careful-claude",
      name = "Careful Claude",
      providerId = AgentSessionProvider.from("claude").value,
    )
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

  private fun ensurePromptExtensionPoints() {
    registerExtensionPointIfNeeded(PROMPT_LAUNCHER_EP, AgentPromptLauncherBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_CONTEXT_CONTRIBUTOR_EP, AgentPromptContextContributorBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_CONTEXT_RENDERER_EP, AgentPromptContextRendererBridge::class.java)
    registerExtensionPointIfNeeded(PALETTE_EXTENSION_EP, AgentPromptPaletteExtension::class.java)
    registerExtensionPointIfNeeded(MANUAL_CONTEXT_SOURCE_EP, AgentPromptManualContextSourceBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_SUGGESTION_AI_BACKEND_EP, AgentPromptSuggestionAiBackend::class.java)
    registerExtensionPointIfNeeded(CONTAINER_LAUNCHER_EP, AgentPromptContainerLauncher::class.java)
  }

  private fun registerExtensionPointIfNeeded(extensionPointName: String, interfaceClass: Class<*>) {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (extensionArea.hasExtensionPoint(extensionPointName)) {
      return
    }

    extensionArea.registerExtensionPoint(
      extensionPointName,
      interfaceClass.name,
      ExtensionPoint.Kind.INTERFACE,
      true,
    )
    registeredExtensionPoints.add(extensionPointName)
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

  private class RecordingPromptLauncher(
    initialPreferences: AgentPromptLauncherBridge.ProviderPreferences,
  ) : AgentPromptLauncherBridge {
    val observedProviders = mutableListOf<AgentSessionProvider>()
    var preferences: AgentPromptLauncherBridge.ProviderPreferences = initialPreferences
      private set

    override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
      return AgentPromptLaunchResult.SUCCESS
    }

    override fun loadProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
      return preferences
    }

    override fun saveProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
      this.preferences = preferences
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
    @JvmField val content: AgentPromptPaletteContent,
    @JvmField val controller: AgentPromptPaletteSessionController,
    @JvmField val promptArea: AgentPromptTextField,
    @JvmField val providerSelector: AgentPromptProviderSelector,
    @JvmField val existingTaskController: AgentPromptExistingTaskController,
    @JvmField val view: AgentPromptPaletteView,
    @JvmField val launcher: RecordingPromptLauncher,
  ) {
    fun dispose() {
      content.dispose("Agent prompt palette session controller test disposed")
    }
  }

  private companion object {
    const val PROMPT_LAUNCHER_EP: String = "com.intellij.agent.workbench.promptLauncher"
    const val PROMPT_CONTEXT_CONTRIBUTOR_EP: String = "com.intellij.agent.workbench.promptContextContributor"
    const val PROMPT_CONTEXT_RENDERER_EP: String = "com.intellij.agent.workbench.promptContextRenderer"
    const val MANUAL_CONTEXT_SOURCE_EP: String = "com.intellij.agent.workbench.promptManualContextSource"
    const val PALETTE_EXTENSION_EP: String = "com.intellij.agent.workbench.promptPaletteExtension"
    const val PROMPT_SUGGESTION_AI_BACKEND_EP: String = "com.intellij.agent.workbench.promptSuggestionAiBackend"
    const val CONTAINER_LAUNCHER_EP: String = "com.intellij.agent.workbench.containerLauncher"
  }
}
