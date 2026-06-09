// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptProviderSelectorTest {
  @BeforeEach
  fun clearProviderAvailabilityCache() {
    ProjectManager.getInstance().defaultProject.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
  }

  @Test
  fun planModeCheckboxUsesMnemonicAndUpdatesStoredSelection() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = listOf(planModeOption()),
      )
      val fixture = createSelectorFixture(listOf(provider))

      fixture.selector.refresh()

      assertThat(fixture.selector.selectedOptionIds(provider.provider)).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(fixture.planModeCheckBox().text).isEqualTo("Plan mode")
      assertThat(fixture.planModeCheckBox().mnemonic).isEqualTo(KeyEvent.VK_P)
      assertThat(fixture.planModeCheckBox().displayedMnemonicIndex).isEqualTo(0)
      assertThat(fixture.planModeCheckBox().text).doesNotContain("Alt+P")
      assertThat(fixture.planModeCheckBox().isSelected).isTrue()
      assertThat(fixture.planModeCheckBox().font).isEqualTo(JBCheckBox().font)
      fixture.view.headerControls.setContainerModeVisible(true)
      val expectedHeaderCheckBox = fixture.headerCheckBox("Run in container")
      assertThat(fixture.planModeCheckBox().border.getBorderInsets(fixture.planModeCheckBox()))
        .isEqualTo(expectedHeaderCheckBox.border.getBorderInsets(expectedHeaderCheckBox))

      fixture.planModeCheckBox().doClick()
      assertThat(fixture.selector.selectedOptionIds(provider.provider)).isEmpty()
      assertThat(fixture.planModeCheckBox().isSelected).isFalse()

      fixture.planModeCheckBox().doClick()
      assertThat(fixture.selector.selectedOptionIds(provider.provider)).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      assertThat(fixture.planModeCheckBox().isSelected).isTrue()
    }
  }

  @Test
  fun providerWithoutPlanModeOptionDoesNotRenderCheckbox() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CLAUDE,
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))

      fixture.selector.refresh()

      assertThat(fixture.view.headerControls.providerOptionActions).isEmpty()
      assertThat(collectComponentsOfType(fixture.view.rootPanel, JBCheckBox::class.java).map { it.text })
        .doesNotContain("Plan mode")
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsControlsUseProviderDefaultsAndVisibility(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.MEDIUM, AgentPromptReasoningEffort.HIGH),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.1-codex", displayName = "GPT-5.1 Codex"),
        ),
      )
      val fixture = withContext(Dispatchers.EDT) {
        createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
      }
      val controller = withContext(Dispatchers.EDT) {
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = {},
        ).also { controller ->
          controller.restoreDefaultSettings(
            mapOf(AgentSessionProvider.CODEX.value to AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH))
          )
        }
      }

      waitForCondition {
        withContext(Dispatchers.EDT) { fixture.view.modelSelectorLink.isVisible }
      }
      withContext(Dispatchers.EDT) {
        assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
        assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
        assertThat(fixture.view.modelSelectorLink.isVisible).isTrue()
        assertThat(fixture.view.modelSelectorLink.isEnabled).isTrue()
        assertThat(fixture.view.modelSelectorLink.text).isEqualTo("Model Default")
        assertThat(fixture.view.reasoningEffortLink.isVisible).isTrue()
        assertThat(fixture.view.reasoningEffortLink.isEnabled).isTrue()
        assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort High")

        controller.setGenerationControlsVisible(false)

        assertThat(fixture.view.generationSettingsPanel.isVisible).isFalse()
        assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
        assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun generationSettingsControlsStayVisibleWhenReasoningEffortIsUnsupported() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.PI,
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = {},
      )

      controller.refreshPresentation()

      assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
      assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
      assertThat(fixture.view.reasoningEffortLink.isVisible).isTrue()
      assertThat(fixture.view.reasoningEffortLink.isEnabled).isFalse()
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(fixture.view.reasoningEffortLink.toolTipText).contains("not available")
    }
  }

  @Test
  fun generationSettingsReasoningEffortPopupActionsUseCodexLabelsAndClearSavedAskAgentDefault() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(
          AgentPromptReasoningEffort.LOW,
          AgentPromptReasoningEffort.HIGH,
          AgentPromptReasoningEffort.XHIGH,
        ),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = {},
      ).also { controller ->
        controller.restoreDefaultSettings(
          mapOf(AgentSessionProvider.CODEX.value to AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH))
        )
      }

      controller.refreshPresentation()
      val actionGroup = checkNotNull(controller.createReasoningEffortActionGroupForTest())
      val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())
      val footerActionTexts = setOf("Save for Ask Agent", "Clear Ask Agent Default")
      val selectionActions = actions.filter { action ->
        action.templatePresentation.text != null && action.templatePresentation.text !in footerActionTexts
      }

      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Extra High")
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "Low", "High", "Extra High", "Clear Ask Agent Default")
      assertThat(actions.last().templatePresentation.description)
        .isEqualTo("Use provider defaults for future Ask Agent launches with this provider.")
      assertThat(selectionActions.map { action -> action.templatePresentation.keepPopupOnPerform })
        .containsOnly(KeepPopupOnPerform.Never)
    }
  }

  @Test
  fun generationSettingsReasoningEffortPopupUsesLoadedCatalogForDefaultModel(): Unit = timeoutRunBlocking {
    val modelCatalogScope = testScope()
    try {
      val fixtureAndController = withContext(Dispatchers.EDT) {
        val provider = testProviderBridge(
          provider = AgentSessionProvider.JUNIE,
          promptOptions = emptyList(),
          availableGenerationModels = listOf(
            AgentPromptGenerationModel(
              id = "chatgpt-5.5",
              displayName = "ChatGPT 5.5",
              supportedReasoningEfforts = setOf(
                AgentPromptReasoningEffort.HIGH,
                AgentPromptReasoningEffort.XHIGH,
              ),
            )
          ),
        )
        val fixture = createSelectorFixture(listOf(provider))
        fixture.selector.refresh()
        val controller = AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = {},
        )
        controller.refreshPresentation()
        fixture to controller
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          fixtureAndController.first.view.modelSelectorLink.isVisible &&
          fixtureAndController.first.view.reasoningEffortLink.isEnabled
        }
      }
      withContext(Dispatchers.EDT) {
        val (_, controller) = fixtureAndController
        val actionGroup = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())

        assertThat(controller.currentSettings()).isEqualTo(AgentPromptGenerationSettings.AUTO)
        assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "High", "Extra High")
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun generationSettingsClearDefaultActionRemovesSavedAskAgentOverride() {
    runInEdtAndWait {
      val providerId = AgentSessionProvider.CODEX.value
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          generationSettingsByProviderId = mapOf(
            providerId to AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH)
          )
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(
          AgentPromptReasoningEffort.HIGH,
          AgentPromptReasoningEffort.XHIGH,
        ),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var defaultSavedNotifications = 0
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { defaultSavedNotifications++ },
      ).also { controller ->
        controller.restoreDefaultSettings(launcher.preferences.generationSettingsByProviderId)
      }

      val clearAction = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Clear Ask Agent Default" }
      clearAction.actionPerformed(TestActionEvent.createTestEvent(clearAction))

      assertThat(defaultSavedNotifications).isEqualTo(1)
      assertThat(launcher.preferences.generationSettingsByProviderId).doesNotContainKey(providerId)
      assertThat(controller.currentSettings()).isEqualTo(AgentPromptGenerationSettings.AUTO)
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      val actions = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "High", "Extra High")
    }
  }

  @Test
  fun generationSettingsDefaultPopupActionSavesChangedAskAgentOverride() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(
          AgentPromptReasoningEffort.HIGH,
          AgentPromptReasoningEffort.XHIGH,
        ),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = {},
      ).also { controller ->
        controller.restoreDefaultSettings(
          mapOf(AgentSessionProvider.CODEX.value to AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH))
        )
      }

      val highAction = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "High" }
      highAction.actionPerformed(TestActionEvent.createTestEvent(highAction))

      val actions = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort High")
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "High", "Extra High", "Save for Ask Agent")
      assertThat(actions.last().templatePresentation.description)
        .isEqualTo("Remember this model and effort for future Ask Agent launches with this provider.")
    }
  }

  @Test
  fun generationSettingsDefaultPopupActionHiddenForProviderDefaultWithoutSavedOverride() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = {},
      )

      controller.refreshPresentation()

      val actions = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "High")
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupActionsCloseOnSelection(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.1-codex", displayName = "GPT-5.1 Codex"),
        ),
      )
      val fixture = withContext(Dispatchers.EDT) {
        createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
      }
      val controller = withContext(Dispatchers.EDT) {
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = {},
        ).also { controller -> controller.refreshPresentation() }
      }

      waitForCondition {
        withContext(Dispatchers.EDT) { controller.createModelActionGroupForTest() != null }
      }
      withContext(Dispatchers.EDT) {
        val actionGroup = checkNotNull(controller.createModelActionGroupForTest())
        val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())

        assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "GPT-5.1 Codex")
        assertThat(actions.map { action -> action.templatePresentation.keepPopupOnPerform })
          .containsOnly(KeepPopupOnPerform.Never)
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun generationSettingsDefaultPopupActionClearsSavedAskAgentOverride() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = {},
      ).also { controller ->
        controller.restoreDefaultSettings(
          mapOf(AgentSessionProvider.CODEX.value to AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH))
        )
      }

      val defaultAction = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Default" }
      defaultAction.actionPerformed(TestActionEvent.createTestEvent(defaultAction))

      val actions = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "High", "Clear Ask Agent Default")
      assertThat(actions.last().templatePresentation.description)
        .isEqualTo("Use provider defaults for future Ask Agent launches with this provider.")
    }
  }

  @Test
  fun chooserGroupAndProviderActionsAreDumbAware() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))

      fixture.selector.refresh()

      val group = fixture.selector.buildChooserActionGroup { error("should not select provider during dumb-awareness test") }
      assertThat(group).isNotNull
      assertThat(DumbService.isDumbAware(group)).isTrue()

      val child = checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).single()
      assertThat(DumbService.isDumbAware(child)).isTrue()
    }
  }

  @Test
  fun disabledProviderActionRetainsUnavailableDescription() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
        cliAvailable = false,
      )
      val fixture = createSelectorFixture(listOf(provider), availabilityByProvider = mapOf(provider.provider to false))

      fixture.selector.refresh()

      val action = checkNotNull(fixture.selector.buildChooserActionGroup { error("should not select unavailable provider") })
        .getChildren(TestActionEvent.createTestEvent())
        .single()
      val event = TestActionEvent.createTestEvent(action)

      action.update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.description).isEqualTo("Codex CLI is unavailable.")
    }
  }

  @Test
  fun promptSelectorExcludesProvidersThatDoNotSupportPromptLaunch() {
    runInEdtAndWait {
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
      )
      val terminalProvider = testProviderBridge(
        provider = AgentSessionProvider.TERMINAL,
        promptOptions = emptyList(),
        supportsPromptLaunch = false,
      )
      val fixture = createSelectorFixture(listOf(terminalProvider, codexProvider))

      fixture.selector.refresh()

      assertThat(fixture.selector.availableProviders).containsExactly(AgentSessionProvider.CODEX)
      assertThat(fixture.selector.selectedProvider?.bridge?.provider?.value).isEqualTo(AgentSessionProvider.CODEX.value)
      val actions = checkNotNull(fixture.selector.buildChooserActionGroup { error("should not select provider during filtering test") })
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.map { action -> action.templatePresentation.text }).containsExactly("Codex")
    }
  }

  @Test
  fun promptSelectorHidesUnavailableDiscoverableProviders() {
    runInEdtAndWait {
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        promptOptions = emptyList(),
      )
      val piProvider = testProviderBridge(
        provider = AgentSessionProvider.PI,
        promptOptions = emptyList(),
        cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
      )
      val fixture = createSelectorFixture(
        providers = listOf(codexProvider, piProvider),
        availabilityByProvider = mapOf(
          AgentSessionProvider.CODEX to true,
          AgentSessionProvider.PI to false,
        ),
      )

      fixture.selector.refresh()

      assertThat(fixture.selector.availableProviders).containsExactly(AgentSessionProvider.CODEX)
      val actions = checkNotNull(fixture.selector.buildChooserActionGroup { error("should not select provider during filtering test") })
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.map { action -> action.templatePresentation.text }).containsExactly("Codex")
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun asyncRefreshAppliesResolvedProviderAvailabilityFromUiScope() = timeoutRunBlocking {
    val provider = testProviderBridge(
      provider = AgentSessionProvider.CODEX,
      promptOptions = emptyList(),
      cliAvailable = false,
    )
    val asyncRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val fixture = withContext(Dispatchers.EDT) {
        createSelectorFixture(listOf(provider), asyncRefreshScope = asyncRefreshScope).also { fixture ->
          fixture.selector.refresh()
        }
      }

      assertThat(withContext(Dispatchers.EDT) { fixture.selector.selectedProvider?.isCliAvailable }).isTrue()
      waitForCondition {
        withContext(Dispatchers.EDT) {
          fixture.selector.selectedProvider?.isCliAvailable == false
        }
      }
    }
    finally {
      asyncRefreshScope.cancel()
    }
  }

  private fun createSelectorFixture(
    providers: List<AgentSessionProviderDescriptor>,
    availabilityByProvider: Map<AgentSessionProvider, Boolean> = emptyMap(),
    asyncRefreshScope: CoroutineScope? = null,
  ): ProviderSelectorFixture {
    val project = ProjectManager.getInstance().defaultProject
    project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(availabilityByProvider)
    val view = createAgentPromptPaletteView(
      promptArea = EditorTextField(),
      contextChipsPanel = JPanel(),
      onProviderIconClicked = {},
      onExistingTaskSelected = {},
    )
    return ProviderSelectorFixture(
      selector = AgentPromptProviderSelector(
        invocationData = AgentPromptInvocationData(
          project = project,
          actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
          actionText = "Ask Agent",
          actionPlace = "MainMenu",
          invokedAtMs = 0L,
        ),
        providerIconLabel = view.providerIconLabel,
        headerControls = view.headerControls,
        providersProvider = { providers },
        sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptProviderSelector::class.java.classLoader),
        asyncRefreshScope = asyncRefreshScope,
      ),
      view = view,
    )
  }

  private suspend fun waitForCondition(condition: suspend () -> Boolean) {
    withTimeout(5.seconds) {
      while (!condition()) {
        delay(10.milliseconds)
      }
    }
  }

  private fun testProviderBridge(
    provider: AgentSessionProvider,
    promptOptions: List<AgentPromptProviderOption>,
    cliAvailable: Boolean = true,
    supportsPromptLaunch: Boolean = true,
    cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.PROMINENT,
    supportedReasoningEffortsOverride: Set<AgentPromptReasoningEffort> = emptySet(),
    availableGenerationModels: List<AgentPromptGenerationModel> = emptyList(),
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = cliVisibilityPolicy
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = displayNameKey
      override val promptOptions: List<AgentPromptProviderOption> = promptOptions
      override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort> = supportedReasoningEffortsOverride
      override val supportsPromptLaunch: Boolean = supportsPromptLaunch
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16

      override suspend fun isCliAvailable(): Boolean = cliAvailable

      override suspend fun listAvailableGenerationModels(project: com.intellij.openapi.project.Project?): List<AgentPromptGenerationModel> {
        return availableGenerationModels
      }

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

  private fun planModeOption(): AgentPromptProviderOption {
    return AgentPromptProviderOption(
      id = AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE,
      labelKey = "toolwindow.prompt.option.plan.mode",
      labelFallback = "Plan mode",
      defaultSelected = true,
    )
  }

  private fun testInvocationData(project: com.intellij.openapi.project.Project): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
    )
  }

  @Suppress("RAW_SCOPE_CREATION")
  private fun testScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  }

  private class TestPromptLauncherBridge(
    initialPreferences: AgentPromptLauncherBridge.ProviderPreferences,
  ) : AgentPromptLauncherBridge {
    var preferences: AgentPromptLauncherBridge.ProviderPreferences = initialPreferences
      private set

    override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
      error("Not required for this test")
    }

    override fun loadProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
      return preferences
    }

    override fun saveProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
      this.preferences = preferences
    }
  }

  private data class ProviderSelectorFixture(
    val selector: AgentPromptProviderSelector,
    val view: AgentPromptPaletteView,
  ) {
    fun planModeCheckBox(): JBCheckBox {
      return headerCheckBox("Plan mode")
    }

    fun headerCheckBox(text: String): JBCheckBox {
      view.headerControls.updateActions()
      layoutPopupRoot(view.rootPanel)
      return collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).single { checkBox -> checkBox.text == text }
    }
  }
}
