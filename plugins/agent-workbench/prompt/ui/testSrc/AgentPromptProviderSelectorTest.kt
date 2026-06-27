// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.icons.AllIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPromptProviderOption
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.builtInLaunchProfileId
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.ui.AgentWorkbenchPopupRow
import com.intellij.agent.workbench.ui.AgentWorkbenchPopupRowRenderer
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.atomic.AtomicInteger
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptProviderSelectorTest {
  @BeforeEach
  fun clearProviderCaches() {
    val project = ProjectManager.getInstance().defaultProject
    project.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
    project.service<AgentPromptGenerationModelCatalogService>().clearForTest()
    runInEdtAndWait {
      service<AgentPromptLaunchProfileEditorWindowService>().clearForTest()
    }
  }

  @Test
  fun planModeCheckboxUsesMnemonicAndUpdatesStoredSelection() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
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
        provider = AgentSessionProvider.from("claude"),
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
  @RegistryKey(key = "agent.workbench.use.monochrome.icons", value = "true")
  fun updatePresentationUsesMonochromeIconWhenRegistryEnabled() {
    runInEdtAndWait {
      val coloredIcon = EmptyIcon.ICON_16
      val monochromeIcon = EmptyIcon.create(18)
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
        icon = coloredIcon,
        monochromeIconOverride = monochromeIcon,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()

      assertThat(providerItemMonochromeIconWithMode(checkNotNull(fixture.selector.selectedMenuItem()))).isSameAs(monochromeIcon)
    }
  }

  @Test
  @RegistryKey(key = "agent.workbench.use.monochrome.icons", value = "false")
  fun updatePresentationUsesColoredIconWhenRegistryDisabled() {
    runInEdtAndWait {
      val coloredIcon = EmptyIcon.ICON_16
      val monochromeIcon = EmptyIcon.create(18)
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
        icon = coloredIcon,
        monochromeIconOverride = monochromeIcon,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()

      assertThat(providerItemMonochromeIconWithMode(checkNotNull(fixture.selector.selectedMenuItem()))).isSameAs(coloredIcon)
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsControlsUseProviderDefaultsAndVisibility(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.MEDIUM, AgentPromptReasoningEffort.HIGH),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.1-codex", displayName = "GPT-5.1 Codex"),
        ),
        onListAvailableGenerationModels = modelCatalogRequests::incrementAndGet,
      )
      val fixture = withContext(Dispatchers.EDT) {
        createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
      }
      val controller = withContext(Dispatchers.EDT) {
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          launchProfileLink = fixture.view.launchProfileLink,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          launchTuningSummaryLink = fixture.view.launchTuningSummaryLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        ).also { controller ->
          val profile = AgentPromptLaunchProfile(
            id = "user:high",
            name = "High",
            providerId = AgentSessionProvider.from("codex").value,
            generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
          )
          controller.restoreLaunchProfiles(
            AgentPromptLauncherBridge.ProviderPreferences(
              launchProfiles = listOf(profile),
              defaultLaunchProfileId = profile.id,
            )
          )
        }
      }

      waitForCondition {
        withContext(Dispatchers.EDT) { fixture.view.launchProfileLink.text == "High" }
      }
      withContext(Dispatchers.EDT) {
        assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
        assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
        assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
        assertThat(fixture.view.modelSelectorLink.isEnabled).isTrue()
        assertThat(fixture.view.modelSelectorLink.text).isEqualTo("Model Default")
        assertThat(modelCatalogRequests.get()).isZero()
        assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
        assertThat(fixture.view.reasoningEffortLink.isEnabled).isTrue()
        assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort High")
        assertThat(fixture.view.launchTuningSummaryLink.isVisible).isFalse()
        assertThat(fixture.view.launchTuningSummaryLink.isEnabled).isTrue()
        assertThat(fixture.view.launchTuningSummaryLink.text).isEqualTo("Model and reasoning")
        assertThat(fixture.view.launchTuningSummaryLink.accessibleContext.accessibleName)
          .isEqualTo("Model and reasoning: Default model · High")
        assertThat(fixture.view.launchProfileLink.text).isEqualTo("High")
        assertThat(fixture.view.launchProfileLink.accessibleContext.accessibleName)
          .isEqualTo("Launch settings: High")

        val reasoningActions = checkNotNull(controller.createReasoningEffortActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(isSelectedInPopup(reasoningActions.single { action -> action.templatePresentation.text == "Default" })).isFalse()
        assertThat(isSelectedInPopup(reasoningActions.single { action -> action.templatePresentation.text == "High" })).isTrue()

        val tuningActions = checkNotNull(controller.createLaunchTuningActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(modelActionEntries(tuningActions)).containsExactly(
          "separator:Model",
          "model:Default",
          "separator:Reasoning",
          "model:Default",
          "model:Medium",
          "model:High",
        )
        assertThat(isSelectedInPopup(tuningActions[1])).isTrue()
        assertThat(isSelectedInPopup(tuningActions[3])).isFalse()
        assertThat(isSelectedInPopup(tuningActions[5])).isTrue()

        val modelSubmenu = launchSettingsModelSubmenu(controller)
        assertThat(modelSubmenu.text).isEqualTo("Default")
        assertThat(modelSubmenu.separatorText).isEqualTo("")
        assertThat(modelSubmenu.secondaryIcon).isSameAs(AllIcons.General.ChevronRight)
        assertThat(popupRowEntries(modelSubmenu.subRows)).containsExactly(
          "separator:Model",
          "row:Default",
        )
        assertThat(popupCommand(modelSubmenu.subRows, "Default").selected).isTrue()

        val workbenchModelSubmenu = launchSettingsWorkbenchModelSubmenu(controller)
        assertThat(workbenchModelSubmenu.text).isEqualTo(modelSubmenu.text)
        assertThat(workbenchModelSubmenu.subRows.map { row -> row.text }).containsExactly("Default")
        assertThat(workbenchModelSubmenu.subRowsProvider != null).isTrue()

        controller.setGenerationControlsVisible(false)

        assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
        assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
        assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
        assertThat(fixture.view.launchTuningSummaryLink.isVisible).isFalse()
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun launchTuningPopupMarksProfileModelAndReasoningSelections() {
    runInEdtAndWait {
      val modelId = "gpt-5.1-codex"
      val profile = AgentPromptLaunchProfile(
        id = "user:profile-model",
        name = "Profile Model",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(
          modelId = modelId,
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        ),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
        supportsGenerationModelSelection = true,
        displayNameForGenerationModelId = { id -> if (id == modelId) "GPT-5.1 Codex" else null },
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        launchTuningSummaryLink = fixture.view.launchTuningSummaryLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)
      val actions = checkNotNull(controller.createLaunchTuningActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())

      assertThat(controller.currentSettings().modelId).isEqualTo(modelId)
      assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Profile Model")
      assertThat(fixture.view.launchProfileLink.accessibleContext.accessibleName)
        .isEqualTo("Launch settings: Profile Model")
      assertThat(fixture.view.launchTuningSummaryLink.text).isEqualTo("Model and reasoning")
      assertThat(fixture.view.launchTuningSummaryLink.accessibleContext.accessibleName)
        .isEqualTo("Model and reasoning: GPT-5.1 Codex · High")
      assertThat(modelActionEntries(actions)).containsExactly(
        "separator:Model",
        "model:Default",
        "separator:Other",
        "model:GPT-5.1 Codex",
        "separator:Reasoning",
        "model:Default",
        "model:High",
      )
      assertThat(isSelectedInPopup(actions[1])).isFalse()
      assertThat(isSelectedInPopup(actions[3])).isTrue()
      assertThat(isSelectedInPopup(actions[5])).isFalse()
      assertThat(isSelectedInPopup(actions[6])).isTrue()

      val modelSubmenu = launchSettingsModelSubmenu(controller)
      assertThat(modelSubmenu.text).isEqualTo("GPT-5.1 Codex")
      assertThat(modelSubmenu.separatorText).isEqualTo("")
      assertThat(modelSubmenu.secondaryIcon).isSameAs(AllIcons.General.ChevronRight)
      assertThat(popupRowEntries(modelSubmenu.subRows)).containsExactly(
        "separator:Model",
        "row:Default",
        "separator:Other",
        "row:GPT-5.1 Codex",
      )
      assertThat(popupCommand(modelSubmenu.subRows, "Default").selected).isFalse()
      assertThat(popupCommand(modelSubmenu.subRows, "GPT-5.1 Codex").selected).isTrue()

      val workbenchModelSubmenu = launchSettingsWorkbenchModelSubmenu(controller)
      assertThat(workbenchModelSubmenu.text).isEqualTo(modelSubmenu.text)
      assertThat(workbenchModelSubmenu.subRows.map { row -> row.text }).containsExactly("Default", "GPT-5.1 Codex")
      assertThat(workbenchModelSubmenu.subRowsProvider != null).isTrue()
    }
  }

  @Test
  fun providerSelectorStaysVisibleWhileGenerationControlsAreHidden() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        launchTuningSummaryLink = fixture.view.launchTuningSummaryLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )

      // AI Review and other provider-aware extension tabs keep the provider selector while hiding the
      // per-task model/reasoning controls their submit action does not consume.
      controller.setControlsVisibility(providerSelectorVisible = true, generationControlsVisible = false)

      assertThat(fixture.view.launchProfileLink.isVisible).isTrue()
      assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
      assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
      assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
    }
  }

  @Test
  fun generationSettingsControlsStayVisibleWhenReasoningEffortIsUnsupported() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        launchTuningSummaryLink = fixture.view.launchTuningSummaryLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )

      controller.refreshPresentation()

      assertThat(fixture.view.generationSettingsPanel.isVisible).isTrue()
      assertThat(fixture.view.launchTuningSummaryLink.isVisible).isFalse()
      assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
      assertThat(fixture.view.reasoningEffortLink.isVisible).isFalse()
      assertThat(fixture.view.reasoningEffortLink.isEnabled).isFalse()
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(fixture.view.reasoningEffortLink.toolTipText).contains("not available")
    }
  }

  @Test
  fun generationSettingsReasoningEffortPopupActionsUseCodexLabelsAndStayTransient() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
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
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )

      controller.refreshPresentation()
      val actionGroup = checkNotNull(controller.createReasoningEffortActionGroupForTest())
      val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())

      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "Low", "High", "Extra High")
      assertThat(actions.map { action -> action.templatePresentation.keepPopupOnPerform })
        .containsOnly(KeepPopupOnPerform.Never)

      val extraHighAction = actions.single { action -> action.templatePresentation.text == "Extra High" }
      extraHighAction.actionPerformed(TestActionEvent.createTestEvent(extraHighAction))

      assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.XHIGH)
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort Extra High")
      assertThat(checkNotNull(controller.createReasoningEffortActionGroupForTest())
                   .getChildren(TestActionEvent.createTestEvent())
                   .mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Default", "Low", "High", "Extra High")
    }
  }

  @Test
  fun generationSettingsReasoningEffortPopupUsesLoadedCatalogForDefaultModel(): Unit = timeoutRunBlocking {
    val modelCatalogScope = testScope()
    try {
      val fixtureAndController = withContext(Dispatchers.EDT) {
        val provider = testProviderBridge(
          provider = AgentSessionProvider.from("junie"),
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
          onDefaultSaved = { _ -> },
        )
        controller.refreshPresentation()
        controller.createModelActionGroupForTest(loadIfNeeded = true)
        fixture to controller
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          val (fixture, controller) = fixtureAndController
          fixture.view.reasoningEffortLink.isEnabled &&
          controller.createModelActionGroupForTest()
            ?.getChildren(TestActionEvent.createTestEvent())
            ?.any { action -> action.templatePresentation.text == "ChatGPT 5.5" } == true
        }
      }
      withContext(Dispatchers.EDT) {
        val (_, controller) = fixtureAndController
        val actionGroup = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())
        val modelActions = checkNotNull(controller.createModelActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
          .filterNot { action -> action is Separator }

        assertThat(controller.currentSettings()).isEqualTo(AgentPromptGenerationSettings.AUTO)
        assertThat(modelActions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "ChatGPT 5.5")
        assertThat(isSelectedInPopup(modelActions.single { action -> action.templatePresentation.text == "Default" })).isTrue()
        assertThat(isSelectedInPopup(modelActions.single { action -> action.templatePresentation.text == "ChatGPT 5.5" })).isFalse()
        assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "High", "Extra High")
        assertThat(isSelectedInPopup(actions.single { action -> action.templatePresentation.text == "Default" })).isTrue()
        assertThat(isSelectedInPopup(actions.single { action -> action.templatePresentation.text == "High" })).isFalse()
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun launchProfilePopupShowsProfilesOnlySplitByLaunchMode() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedLaunchModesOverride = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
        supportsGenerationModelSelection = true,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)

      val actions = controller.createLaunchProfileActionGroupForTest().getChildren(TestActionEvent.createTestEvent())
      val profileActions = actions.filterNot { action -> action is Separator }
      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Careful")
      assertThat(fixture.view.reasoningEffortLink.text).isEqualTo("Effort High")
      assertThat(profileActions.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Codex", "Careful", "Codex (Full Auto)", "Manage Launch Profiles…")
      assertThat(profileActions.last().templatePresentation.keepPopupOnPerform).isEqualTo(KeepPopupOnPerform.Never)
      assertThat(actions.filterIsInstance<Separator>()).hasSize(3)
    }
  }

  @Test
  fun launchProfilePopupRowsKeepDefaultMarkerSeparateFromCurrentSelection() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
        supportsGenerationModelSelection = true,
      )
      val defaultProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(defaultProfile),
          defaultLaunchProfileId = defaultProfile.id,
        )
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)

      val standardAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Codex" }
      standardAction.actionPerformed(TestActionEvent.createTestEvent(standardAction))

      val rows = controller.createLaunchProfilePopupRowsForTest()
      val popupStep = controller.createLaunchProfilePopupStepForTest()

      val currentRow = rows.single { row -> row.text == "Codex" }
      assertThat(currentRow.selected).isTrue()
      assertThat(currentRow.primaryIcon).isNotNull()
      val workbenchRow = AgentWorkbenchPopupRow(text = currentRow.text)
      assertThat(popupStep.getIconFor(workbenchRow)).isNull()
      assertThat(popupStep.getSelectedIconFor(workbenchRow)).isNull()
      val defaultRow = rows.single { row -> row.text == "Careful" }
      assertThat(defaultRow.selected).isFalse()
      assertThat(defaultRow.marksDefaultProfile).isTrue()
      assertThat(defaultRow.secondaryIcon).isNotNull()
      assertThat(defaultRow.tooltipText).isEqualTo("Default profile")

      val renderedCurrentRow = AgentWorkbenchPopupRowRenderer()
        .getListCellRendererComponent(JList(arrayOf(workbenchRow)), workbenchRow, 0, true, true)

      assertThat(renderedCurrentRow).isInstanceOf(SelectablePanel::class.java)
      assertThat((renderedCurrentRow as SelectablePanel).selectionColor).isNotNull()
    }
  }

  @Test
  fun manageLaunchProfilesActionUsesInjectedDialogRunner() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var suppliedDialog: AgentPromptLaunchProfileEditorOpenDialog? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
        manageProfilesDialogRunner = { openDialog -> suppliedDialog = openDialog },
      )

      val manageAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Manage Launch Profiles…" }
      manageAction.actionPerformed(TestActionEvent.createTestEvent(manageAction))

      assertThat(manageAction.templatePresentation.keepPopupOnPerform).isEqualTo(KeepPopupOnPerform.Never)
      assertThat(suppliedDialog != null).isTrue()
    }
  }

  @Test
  fun manageLaunchProfilesActionReusesOpenNonModalDialogAcrossControllers() {
    runInEdtAndWait {
      val editorService = service<AgentPromptLaunchProfileEditorWindowService>()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val firstController = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
        manageProfilesDialogRunner = { openDialog -> openDialog(null) },
      )
      val secondFixture = createSelectorFixture(listOf(provider))
      secondFixture.selector.refresh()
      val secondController = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = secondFixture.selector,
        generationSettingsPanel = secondFixture.view.generationSettingsPanel,
        launchProfileLink = secondFixture.view.launchProfileLink,
        modelSelectorLink = secondFixture.view.modelSelectorLink,
        reasoningEffortLink = secondFixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
        manageProfilesDialogRunner = { openDialog -> openDialog(null) },
      )
      try {
        firstController.openManageProfilesDialogForTest()
        val firstDialog = checkNotNull(editorService.activeDialogForTest())
        assertThat(firstDialog.isModalForTest()).isFalse()

        secondController.openManageProfilesDialogForTest()

        assertThat(editorService.activeDialogForTest()).isSameAs(firstDialog)
      }
      finally {
        editorService.clearForTest()
      }
    }
  }

  @Test
  fun manageLaunchProfilesDialogRestoresPromptOnlyAfterClose() {
    runInEdtAndWait {
      val editorService = service<AgentPromptLaunchProfileEditorWindowService>()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )
      var restoreCount = 0
      try {
        controller.openManageProfilesDialogForTest { restoreCount++ }
        val firstDialog = checkNotNull(editorService.activeDialogForTest())

        assertThat(restoreCount).isZero()

        firstDialog.closeForTest()

        assertThat(restoreCount).isOne()

        controller.openManageProfilesDialogForTest()
        checkNotNull(editorService.activeDialogForTest()).closeForTest()

        assertThat(restoreCount).isOne()
      }
      finally {
        editorService.clearForTest()
      }
    }
  }

  @Test
  fun manageLaunchProfilesDialogUsesLatestPromptRestoreCallback() {
    runInEdtAndWait {
      val editorService = service<AgentPromptLaunchProfileEditorWindowService>()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )
      var firstRestoreCount = 0
      var secondRestoreCount = 0
      try {
        controller.openManageProfilesDialogForTest { firstRestoreCount++ }
        controller.openManageProfilesDialogForTest { secondRestoreCount++ }

        checkNotNull(editorService.activeDialogForTest()).closeForTest()

        assertThat(firstRestoreCount).isZero()
        assertThat(secondRestoreCount).isOne()
      }
      finally {
        editorService.clearForTest()
      }
    }
  }

  @Test
  fun launchProfilePopupShowsProviderIconsWithSelectedBadgeAfterPopupUpdate() {
    runInEdtAndWait {
      val providerIcon = EmptyIcon.create(17)
      val activeProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
      val userProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        launchMode = AgentSessionLaunchMode.STANDARD,
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(userProfile),
          defaultLaunchProfileId = activeProfileId,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        icon = providerIcon,
        monochromeIconOverride = providerIcon,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val profileActions = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
      val selectedAction = profileActions.single { action -> action.templatePresentation.text == "Codex" }
      val unselectedAction = profileActions.single { action -> action.templatePresentation.text == "Careful" }
      val selectedPopupEvent = popupEvent(selectedAction)
      val unselectedPopupEvent = popupEvent(unselectedAction)

      assertThat(selectedAction.templatePresentation.icon).isSameAs(providerIcon)
      assertThat(selectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
      assertThat(unselectedAction.templatePresentation.icon).isSameAs(providerIcon)
      assertThat(unselectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()

      selectedAction.update(selectedPopupEvent)
      unselectedAction.update(unselectedPopupEvent)

      assertThat(Toggleable.isSelected(selectedPopupEvent.presentation)).isTrue()
      assertThat(selectedPopupEvent.presentation.icon).isSameAs(providerIcon)
      assertThat(selectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
      assertThat(Toggleable.isSelected(unselectedPopupEvent.presentation)).isFalse()
      assertThat(unselectedPopupEvent.presentation.icon).isSameAs(providerIcon)
      assertThat(unselectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
    }
  }

  @Test
  fun launchProfilePopupShowsImplicitDefaultBadgeOnFirstOpen() {
    runInEdtAndWait {
      val providerIcon = EmptyIcon.create(17)
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        icon = providerIcon,
        monochromeIconOverride = providerIcon,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val profileActions = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
      val selectedAction = profileActions.single { action -> action.templatePresentation.text == "Codex" }
      val selectedPopupEvent = popupEvent(selectedAction)

      assertThat(launcher.preferences.defaultLaunchProfileId).isNull()
      assertThat(selectedAction.templatePresentation.icon).isSameAs(providerIcon)
      assertThat(selectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()

      selectedAction.update(selectedPopupEvent)

      assertThat(Toggleable.isSelected(selectedPopupEvent.presentation)).isTrue()
      assertThat(selectedPopupEvent.presentation.icon).isSameAs(providerIcon)
      assertThat(selectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
      assertThat(launcher.preferences.defaultLaunchProfileId).isNull()
    }
  }

  @Test
  fun launchProfileSelectionIsCurrentTaskOnly() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption(defaultSelected = false)),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      assertThat(fixture.selector.isPlanModeSelected()).isFalse()
      val builtInAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Codex" }

      fixture.selector.setPlanModeSelected(true)
      builtInAction.actionPerformed(TestActionEvent.createTestEvent(builtInAction))

      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(profile.id)
      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Default")
      assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.AUTO)
      assertThat(fixture.selector.isPlanModeSelected()).isTrue()
    }
  }

  @Test
  fun launchProfileApplicationRefreshesContainerModeForSelectedProvider() {
    runInEdtAndWait {
      val codexProfile = AgentPromptLaunchProfile(
        id = "user:codex",
        name = "Codex Profile",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(codexProfile),
          defaultLaunchProfileId = codexProfile.id,
        )
      )
      val claudeProvider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
      )
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      lateinit var fixture: ProviderSelectorFixture
      fixture = createSelectorFixture(
        providers = listOf(claudeProvider, codexProvider),
        onProviderSelectionChanged = { syncContainerModeForTest(fixture) },
      )
      fixture.selector.refresh()
      fixture.view.headerControls.setContainerModeState(visible = true, enabled = true, selected = true, tooltipText = null)
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)

      assertThat(fixture.selector.selectedProvider?.bridge?.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(fixture.view.containerModeAction.visible).isFalse()
      assertThat(fixture.view.containerModeAction.selected).isFalse()
    }
  }

  @Test
  fun restoredContainerModeSelectionIsClampedForUnsupportedProvider() {
    runInEdtAndWait {
      val claudeProvider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
      )
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(claudeProvider, codexProvider))
      fixture.selector.refresh()
      fixture.selector.selectProvider(AgentSessionProvider.from("codex"))

      syncContainerModeForTest(fixture, requestedSelection = true)

      assertThat(fixture.selector.selectedProvider?.bridge?.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(fixture.view.containerModeAction.visible).isFalse()
      assertThat(fixture.view.containerModeAction.selected).isFalse()
    }
  }

  @Test
  fun launchProfilePopupOmitsUnavailableProfiles() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        cliAvailable = false,
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider), availabilityByProvider = mapOf(provider.provider to false))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)

      val actions = controller.createLaunchProfileActionGroupForTest().getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.filterNot { action -> action is Separator }.mapNotNull { action -> action.templatePresentation.text })
        .containsExactly("Manage Launch Profiles…")
      assertThat(controller.manageProfilesRowsForTest()).containsExactly(profile)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(profile.id)
        assertThat(editor.profileNamesForTest()).containsExactly("Careful")
        assertThat(editor.isSelectedProfileUnavailableForTest()).isTrue()
        assertThat(editor.isSelectedProfileEditableForTest()).isTrue()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorSavesUserProfileEditsImmediately() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )

      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(profile.id)
        editor.setSelectedProfileNameForTest("Careful Updated")

        assertThat(statusMessage).isEqualTo("Launch profile updated.")
        assertThat(launcher.preferences.launchProfiles.single().name).isEqualTo("Careful Updated")
        assertThat(fixture.view.launchProfileLink.text).isEqualTo("Careful Updated")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorSavesAndRemovesBuiltInOverrides() {
    runInEdtAndWait {
      val builtInProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(builtInProfileId)
        editor.setSelectedProfileNameForTest("Careful Codex")

        val savedProfile = launcher.preferences.launchProfiles.single()
        assertThat(savedProfile.id).isEqualTo(builtInProfileId)
        assertThat(savedProfile.kind).isEqualTo(AgentPromptLaunchProfileKind.USER)
        assertThat(savedProfile.name).isEqualTo("Careful Codex")
        assertThat(controller.manageProfilesRowsForTest().map(AgentPromptLaunchProfile::name))
          .containsExactly("Careful Codex")

        editor.setSelectedProfileNameForTest("Codex")

        assertThat(launcher.preferences.launchProfiles).isEmpty()
        assertThat(controller.manageProfilesRowsForTest().map(AgentPromptLaunchProfile::name))
          .containsExactly("Codex")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorKeepsInvalidNameTransient() {
    runInEdtAndWait {
      val carefulProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val fastProfile = AgentPromptLaunchProfile(
        id = "user:fast",
        name = "Fast",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val updatedProfiles = ArrayList<AgentPromptLaunchProfile>()
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(carefulProfile, fastProfile),
        activeProfileId = carefulProfile.id,
        onUpdateProfile = { profile -> updatedProfiles += profile },
      )
      try {
        editor.selectProfileForTest(carefulProfile.id)
        editor.setSelectedProfileNameForTest("")

        assertThat(updatedProfiles).isEmpty()
        assertThat(editor.profileNamesForTest()).containsExactly("Careful", "Fast")
        assertThat(editor.selectedProfileNameForTest()).isEmpty()

        editor.selectProfileForTest(fastProfile.id)
        assertThat(editor.selectedProfileNameForTest()).isEqualTo("Fast")

        editor.selectProfileForTest(carefulProfile.id)
        assertThat(editor.selectedProfileNameForTest()).isEqualTo("Careful")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorResetsModelWhenProviderChanges() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(modelId = "codex-model"),
      )
      val updatedProfiles = ArrayList<AgentPromptLaunchProfile>()
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        availableGenerationModels = listOf(AgentPromptGenerationModel("codex-model", "Codex Model")),
      )
      val claudeProvider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
        availableGenerationModels = listOf(AgentPromptGenerationModel("claude-model", "Claude Model")),
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        providerOverrides = listOf(codexProvider, claudeProvider),
        modelCatalogStateProvider = { providerId ->
          when (providerId) {
            AgentSessionProvider.from("codex").value -> AgentPromptGenerationModelCatalogState.Loaded(
              listOf(AgentPromptGenerationModel("codex-model", "Codex Model"))
            )
            AgentSessionProvider.from("claude").value -> AgentPromptGenerationModelCatalogState.Loaded(
              listOf(AgentPromptGenerationModel("claude-model", "Claude Model"))
            )
            else -> null
          }
        },
        onUpdateProfile = { updatedProfile -> updatedProfiles += updatedProfile },
      )
      try {
        editor.selectProfileForTest(profile.id)
        editor.selectSelectedProfileProviderForTest(AgentSessionProvider.from("claude").value)

        val updatedProfile = updatedProfiles.single()
        assertThat(updatedProfile.providerId).isEqualTo(AgentSessionProvider.from("claude").value)
        assertThat(updatedProfile.generationSettings.modelId).isNull()
        assertThat(editor.selectedProfileModelIdForTest()).isNull()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorCopyCreatesUserProfileImmediately() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD),
        name = "Codex",
        kind = AgentPromptLaunchProfileKind.BUILT_IN,
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val createdProfiles = ArrayList<AgentPromptLaunchProfile>()
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        supportedLaunchModes = setOf(AgentSessionLaunchMode.STANDARD),
        supportedReasoningEfforts = setOf(AgentPromptReasoningEffort.HIGH),
        onCreateProfile = { createdProfiles += it },
      )
      try {
        editor.selectProfileForTest(profile.id)
        editor.copySelectedProfileForTest()

        val createdProfile = createdProfiles.single()
        assertThat(createdProfile.id).isEqualTo("user:new")
        assertThat(createdProfile.name).isEqualTo("High")
        assertThat(createdProfile.kind).isEqualTo(AgentPromptLaunchProfileKind.USER)
        assertThat(createdProfile.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
        assertThat(createdProfile.generationSettings.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
        assertThat(editor.profileNamesForTest()).containsExactly("Codex", "High")
        assertThat(editor.isNameFieldTextSelectedForTest()).isTrue()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorCopyPreservesPlanEffort() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(planReasoningEffort = AgentPromptReasoningEffort.AUTO),
      )
      val createdProfiles = ArrayList<AgentPromptLaunchProfile>()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
        supportsPlanReasoningEffortOverride = true,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        providerOverride = provider,
        onCreateProfile = { createdProfile -> createdProfiles += createdProfile },
      )
      try {
        editor.selectProfileForTest(profile.id)
        editor.copySelectedProfileForTest()

        assertThat(createdProfiles.single().generationSettings.planReasoningEffort).isEqualTo(AgentPromptReasoningEffort.AUTO)
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorReasoningEffortCustomizesBuiltInProfile() {
    runInEdtAndWait {
      val builtInProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(builtInProfileId)
        editor.selectReasoningEffortForTest(AgentPromptReasoningEffort.HIGH)

        assertThat(launcher.preferences.launchProfiles.single().generationSettings.reasoningEffort)
          .isEqualTo(AgentPromptReasoningEffort.HIGH)

        editor.selectReasoningEffortForTest(AgentPromptReasoningEffort.AUTO)

        assertThat(launcher.preferences.launchProfiles).isEmpty()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorPlanEffortCustomizesBuiltInProfile() {
    runInEdtAndWait {
      val builtInProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
        supportsPlanReasoningEffortOverride = true,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(builtInProfileId)
        editor.selectPlanEffortForTest(AgentPromptReasoningEffort.AUTO)

        assertThat(launcher.preferences.launchProfiles.single().generationSettings.planReasoningEffort)
          .isEqualTo(AgentPromptReasoningEffort.AUTO)

        editor.selectPlanEffortForTest(null)

        assertThat(launcher.preferences.launchProfiles).isEmpty()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorSavesPlanEffortForPlanCapableProvider() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val updatedProfiles = ArrayList<AgentPromptLaunchProfile>()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH, AgentPromptReasoningEffort.XHIGH),
        supportsPlanReasoningEffortOverride = true,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        providerOverride = provider,
        onUpdateProfile = { updatedProfile -> updatedProfiles += updatedProfile },
      )
      try {
        editor.selectProfileForTest(profile.id)

        assertThat(editor.isPlanEffortVisibleForTest()).isTrue()
        assertThat(editor.planEffortOptionTextsForTest())
          .containsExactly("Same as Effort", "Provider Default", "High", "Extra High")

        editor.selectPlanEffortForTest(AgentPromptReasoningEffort.XHIGH)

        val updatedProfile = updatedProfiles.single()
        assertThat(updatedProfile.generationSettings.planReasoningEffort).isEqualTo(AgentPromptReasoningEffort.XHIGH)
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorHidesPlanEffortForProviderWithoutPlanEffortSupport() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        providerOverride = provider,
      )
      try {
        editor.selectProfileForTest(profile.id)

        assertThat(editor.isPlanEffortVisibleForTest()).isFalse()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorIsNonModal() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
      )
      try {
        assertThat(editor.isModalForTest()).isFalse()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorSelectionDoesNotApplyProfileToCurrentTask() {
    runInEdtAndWait {
      val carefulProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val fastProfile = AgentPromptLaunchProfile(
        id = "user:fast",
        name = "Fast",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.LOW),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(carefulProfile, fastProfile),
          defaultLaunchProfileId = carefulProfile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.LOW, AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(fastProfile.id)

        assertThat(fixture.view.launchProfileLink.text).isEqualTo("Careful")
        assertThat(controller.currentSettings().reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
        assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(carefulProfile.id)
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorRenameActionSelectsEditableProfileName() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
      )
      try {
        editor.selectProfileForTest(profile.id)
        editor.renameSelectedProfileForTest()

        assertThat(editor.isNameFieldTextSelectedForTest()).isTrue()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorDeletesOnlyUserProfiles() {
    runInEdtAndWait {
      val userProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val builtInProfile = AgentPromptLaunchProfile(
        id = "builtin:codex",
        name = "Default Profile",
        kind = AgentPromptLaunchProfileKind.BUILT_IN,
        providerId = AgentSessionProvider.from("codex").value,
      )
      val deletedProfiles = ArrayList<AgentPromptLaunchProfile>()
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(builtInProfile, userProfile),
        activeProfileId = builtInProfile.id,
        defaultProfileId = userProfile.id,
        onDeleteProfile = { profile ->
          deletedProfiles += profile
          true
        },
      )
      try {
        editor.selectProfileForTest(builtInProfile.id)
        assertThat(editor.isSelectedProfileRemovableForTest()).isFalse()
        editor.deleteSelectedProfileForTest()
        assertThat(deletedProfiles).isEmpty()
        assertThat(editor.profileNamesForTest()).containsExactly("Default Profile", "Careful")

        editor.selectProfileForTest(userProfile.id)
        assertThat(editor.isSelectedProfileRemovableForTest()).isTrue()
        editor.deleteSelectedProfileForTest()
        assertThat(deletedProfiles).containsExactly(userProfile)
        assertThat(editor.profileNamesForTest()).containsExactly("Default Profile")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorKeepsProfileWhenDeleteCallbackDeclines() {
    runInEdtAndWait {
      val userProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(userProfile),
        activeProfileId = userProfile.id,
        onDeleteProfile = { false },
      )
      try {
        editor.selectProfileForTest(userProfile.id)
        editor.deleteSelectedProfileForTest()

        assertThat(editor.profileNamesForTest()).containsExactly("Careful")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorKeepsProfileWhenDeleteConfirmationIsCancelled() {
    runInEdtAndWait {
      val userProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(launchProfiles = listOf(userProfile))
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val editor = controller.createManageProfilesDialogForTest()
      try {
        editor.selectProfileForTest(userProfile.id)
        withNoTestDialog {
          editor.deleteSelectedProfileForTest()
        }

        assertThat(launcher.preferences.launchProfiles).containsExactly(userProfile)
        assertThat(editor.profileNamesForTest()).contains("Careful")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorListKeepsDefaultMarkerOutOfRowText() {
    runInEdtAndWait {
      val defaultProfile = AgentPromptLaunchProfile(
        id = "user:brave",
        name = "Junie (Brave Mode) 124323",
        providerId = AgentSessionProvider.from("codex").value,
        launchMode = AgentSessionLaunchMode.YOLO,
      )
      val otherProfile = AgentPromptLaunchProfile(
        id = "user:standard",
        name = "Codex Standard",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(defaultProfile, otherProfile),
        activeProfileId = defaultProfile.id,
        defaultProfileId = defaultProfile.id,
        supportedLaunchModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      )
      try {
        assertThat(editor.profileListFixedCellWidthForTest()).isEqualTo(JBUI.scale(260))
        assertThat(editor.profileListRendererTextForTest(defaultProfile.id)).isEqualTo(defaultProfile.name)
        assertThat(editor.profileListRendererTextForTest(otherProfile.id)).isEqualTo(otherProfile.name)
        assertThat(editor.isProfileListRendererNameBoldForTest(defaultProfile.id)).isTrue()
        assertThat(editor.isProfileListRendererNameBoldForTest(otherProfile.id)).isFalse()
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorModelComboGroupsModelsBySource() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:grouped",
        name = "Grouped",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(modelId = "gpt-5.5"),
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        modelCatalog = listOf(
          AgentPromptGenerationModel(id = "claude-opus-4-8", displayName = "Claude Opus")
            .withGroup(AgentPromptGenerationModelGroup.CLAUDE_CODE),
          AgentPromptGenerationModel(id = "custom-model", displayName = "Custom Model"),
          AgentPromptGenerationModel(id = "qwen-local", displayName = "Qwen Local").withGroup(AgentPromptGenerationModelGroup.LOCAL),
          AgentPromptGenerationModel(id = "gpt-5.5", displayName = "GPT-5.5").withGroup(AgentPromptGenerationModelGroup.OPENAI),
        ),
      )
      try {
        editor.selectProfileForTest(profile.id)

        assertThat(editor.modelOptionTextsForTest()).containsExactly(
          "Default",
          "Qwen Local",
          "GPT-5.5",
          "Claude Opus",
          "Custom Model",
        )
        assertThat(editor.modelOptionSeparatorTextsForTest()).containsExactly(
          null,
          "Local",
          "OpenAI",
          "Claude Code",
          "Other",
        )
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorAllowsCustomModelId() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:custom-model",
        name = "Custom Model",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val updatedProfiles = ArrayList<AgentPromptLaunchProfile>()
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        modelCatalog = listOf(
          AgentPromptGenerationModel(id = "gpt-5.5", displayName = "GPT-5.5").withGroup(AgentPromptGenerationModelGroup.OPENAI),
        ),
        onUpdateProfile = { updatedProfile -> updatedProfiles += updatedProfile },
      )
      try {
        editor.selectProfileForTest(profile.id)
        editor.setSelectedProfileModelIdForTest("claude-future-6")

        assertThat(editor.isModelComboEditableForTest()).isTrue()
        assertThat(editor.selectedProfileModelIdForTest()).isEqualTo("claude-future-6")
        assertThat(updatedProfiles.last().generationSettings.modelId).isEqualTo("claude-future-6")
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorModelComboGroupsUnknownSelectedModelAsOther() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:unknown-model",
        name = "Unknown Model",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(modelId = "saved-custom-model"),
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        modelCatalog = listOf(
          AgentPromptGenerationModel(id = "qwen-local", displayName = "Qwen Local").withGroup(AgentPromptGenerationModelGroup.LOCAL),
          AgentPromptGenerationModel(id = "gpt-5.5", displayName = "GPT-5.5").withGroup(AgentPromptGenerationModelGroup.OPENAI),
        ),
      )
      try {
        editor.selectProfileForTest(profile.id)

        assertThat(editor.modelOptionTextsForTest()).containsExactly(
          "Default",
          "Qwen Local",
          "GPT-5.5",
          "saved-custom-model",
        )
        assertThat(editor.modelOptionSeparatorTextsForTest()).containsExactly(
          null,
          "Local",
          "OpenAI",
          "Other",
        )
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorUsesProviderDisplayNameForUnknownSelectedModel() {
    runInEdtAndWait {
      val encodedModelId = "pi:eyJmb3JtYXRWZXJzaW9uIjoxLCJwcm92aWRlciI6IkpldEJyYWlucyBDZW50cmFsIiwibW9kZWxJZCI6ImdwdC01LjUifQ"
      val profile = AgentPromptLaunchProfile(
        id = "user:encoded-model",
        name = "Encoded Model",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(modelId = encodedModelId),
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        displayNameForGenerationModelId = { modelId ->
          if (modelId == encodedModelId) "GPT-5.5 (JetBrains Central)" else null
        },
      )
      val editor = createLaunchProfileEditorForTest(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        providerOverride = provider,
      )
      try {
        editor.selectProfileForTest(profile.id)

        assertThat(editor.selectedProfileModelIdForTest()).isEqualTo(encodedModelId)
        assertThat(editor.modelOptionTextsForTest()).containsExactly(
          "Default",
          "GPT-5.5 (JetBrains Central)",
        )
        assertThat(editor.modelOptionTextsForTest()).doesNotContain(encodedModelId)
      }
      finally {
        editor.closeForTest()
      }
    }
  }

  @Test
  fun launchProfileEditorModelComboStartsCatalogLoadingOnOpen(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val catalogService = project.service<AgentPromptGenerationModelCatalogService>()
    val refreshStarted = CompletableDeferred<Unit>()
    val finishRefresh = CompletableDeferred<Unit>()
    val modelCatalogRequests = AtomicInteger()
    val provider = testProviderBridge(
      provider = AgentSessionProvider.from("pi"),
      promptOptions = emptyList(),
      supportsGenerationModelSelection = true,
      availableGenerationModelsResolver = {
        modelCatalogRequests.incrementAndGet()
        refreshStarted.complete(Unit)
        finishRefresh.await()
        listOf(AgentPromptGenerationModel(id = "pi:sonnet", displayName = "Pi Sonnet"))
      },
    )
    val profile = AgentPromptLaunchProfile(
      id = "user:models",
      name = "Models",
      providerId = AgentSessionProvider.from("pi").value,
    )
    var editor: AgentPromptLaunchProfileEditorDialog? = null
    try {
      editor = withContext(Dispatchers.EDT) {
        createLaunchProfileEditorForTest(
          profiles = listOf(profile),
          activeProfileId = profile.id,
          providerOverride = provider,
          modelCatalogStateProvider = catalogService::catalogState,
          requestModelCatalogRefresh = { providerId, onStateChanged ->
            if (providerId == AgentSessionProvider.from("pi").value) {
              catalogService.requestStateRefresh(provider, project, onStateChanged)
            }
          },
        ).also { it.selectProfileForTest(profile.id) }
      }

      withContext(Dispatchers.EDT) {
        val activeEditor = editor
        assertThat(activeEditor.isModelComboLiveUpdateEnabledForTest()).isTrue()
        assertThat(activeEditor.modelOptionTextsForTest()).containsExactly("Default")
        activeEditor.openModelComboForTest()
        assertThat(activeEditor.modelOptionTextsForTest()).containsExactly("Default", "Loading models...")
        assertThat(activeEditor.modelOptionIconsForTest()[0]).isNull()
        assertThat(activeEditor.modelOptionIconsForTest()[1]).isNotNull()
      }
      waitForCondition { refreshStarted.isCompleted }
      finishRefresh.complete(Unit)
      waitForCondition {
        withContext(Dispatchers.EDT) {
          editor.modelOptionTextsForTest() == listOf("Default", "Pi Sonnet")
        }
      }
      assertThat(modelCatalogRequests.get()).isEqualTo(1)
    }
    finally {
      if (!finishRefresh.isCompleted) {
        finishRefresh.complete(Unit)
      }
      withContext(Dispatchers.EDT) { editor?.closeForTest() }
    }
  }

  @Test
  fun launchProfileEditorModeControlShowsYoloOnlyWhenProviderSupportsIt() {
    runInEdtAndWait {
      val yoloProfile = AgentPromptLaunchProfile(
        id = "user:yolo",
        name = "YOLO",
        providerId = AgentSessionProvider.from("codex").value,
        launchMode = AgentSessionLaunchMode.YOLO,
      )
      val yoloEditor = createLaunchProfileEditorForTest(
        profiles = listOf(yoloProfile),
        activeProfileId = yoloProfile.id,
        supportedLaunchModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      )
      try {
        yoloEditor.selectProfileForTest(yoloProfile.id)

        assertThat(yoloEditor.selectedLaunchModeForTest()).isEqualTo(AgentSessionLaunchMode.YOLO)
        assertThat(yoloEditor.isLaunchModeEnabledForTest(AgentSessionLaunchMode.STANDARD)).isTrue()
        assertThat(yoloEditor.isLaunchModeEnabledForTest(AgentSessionLaunchMode.YOLO)).isTrue()
      }
      finally {
        yoloEditor.closeForTest()
      }

      val standardOnlyEditor = createLaunchProfileEditorForTest(
        profiles = listOf(yoloProfile),
        activeProfileId = yoloProfile.id,
        supportedLaunchModes = setOf(AgentSessionLaunchMode.STANDARD),
      )
      try {
        standardOnlyEditor.selectProfileForTest(yoloProfile.id)

        assertThat(standardOnlyEditor.selectedLaunchModeForTest()).isEqualTo(AgentSessionLaunchMode.STANDARD)
        assertThat(standardOnlyEditor.isLaunchModeEnabledForTest(AgentSessionLaunchMode.STANDARD)).isTrue()
        assertThat(standardOnlyEditor.isLaunchModeEnabledForTest(AgentSessionLaunchMode.YOLO)).isFalse()
      }
      finally {
        standardOnlyEditor.closeForTest()
      }
    }
  }

  @Test
  fun providerSelectionNormalizesUnsupportedLaunchMode() {
    runInEdtAndWait {
      val codex = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedLaunchModesOverride = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      )
      val claude = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = emptyList(),
        supportedLaunchModesOverride = setOf(AgentSessionLaunchMode.STANDARD),
      )
      val fixture = createSelectorFixture(listOf(codex, claude))
      fixture.selector.refresh()

      fixture.selector.selectProvider(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.YOLO)
      assertThat(fixture.selector.selectedLaunchMode).isEqualTo(AgentSessionLaunchMode.YOLO)

      fixture.selector.selectProvider(AgentSessionProvider.from("claude"))

      assertThat(fixture.selector.selectedLaunchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    }
  }

  @Test
  fun setDefaultLaunchProfilePersistsCurrentSavedProfile() {
    runInEdtAndWait {
      val carefulProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val fastProfile = AgentPromptLaunchProfile(
        id = "user:fast",
        name = "Fast",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.LOW),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(carefulProfile, fastProfile),
          defaultLaunchProfileId = carefulProfile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.LOW, AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val fastAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Fast" }

      fastAction.actionPerformed(TestActionEvent.createTestEvent(fastAction))
      val defaultSaved = controller.setDefaultProfileForTest(fastProfile.id)

      assertThat(defaultSaved).isTrue()
      assertThat(statusMessage).isEqualTo("Default profile updated.")
      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(fastProfile.id)
      assertThat(launcher.preferences.launchProfiles).containsExactly(carefulProfile, fastProfile)
      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Fast")
    }
  }

  @Test
  fun inlineMakeDefaultActionPersistsSelectedProfile() {
    runInEdtAndWait {
      val carefulProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val fastProfile = AgentPromptLaunchProfile(
        id = "user:fast",
        name = "Fast",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.LOW),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(carefulProfile, fastProfile),
          defaultLaunchProfileId = carefulProfile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.LOW, AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        defaultProfileActionControl = fixture.view.defaultProfileActionControl,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val fastAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Fast" }

      fastAction.actionPerformed(TestActionEvent.createTestEvent(fastAction))

      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
      launchSettingsCommand(controller, "Make Default").onChosen()

      assertThat(statusMessage).isEqualTo("Default profile updated.")
      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(fastProfile.id)
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
    }
  }

  @Test
  fun inlineDefaultActionIsHiddenForImplicitBuiltInDefaultProfile() {
    runInEdtAndWait {
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        defaultProfileActionControl = fixture.view.defaultProfileActionControl,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)

      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Default")
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
    }
  }

  @Test
  fun inlineMakeDefaultActionPersistsSelectedBuiltInProfile() {
    runInEdtAndWait {
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedLaunchModesOverride = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        defaultProfileActionControl = fixture.view.defaultProfileActionControl,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val yoloAction = controller.createLaunchProfileActionGroupForTest()
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Codex (Full Auto)" }

      yoloAction.actionPerformed(TestActionEvent.createTestEvent(yoloAction))

      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Full Auto")
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
      launchSettingsCommand(controller, "Make Default").onChosen()

      assertThat(statusMessage).isEqualTo("Default profile updated.")
      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(
        builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.YOLO)
      )
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
    }
  }

  @Test
  fun inlineSaveAsDefaultActionCreatesGeneratedProfile() {
    runInEdtAndWait {
      val launcher = TestPromptLauncherBridge(AgentPromptLauncherBridge.ProviderPreferences())
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        defaultProfileActionControl = fixture.view.defaultProfileActionControl,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val highAction = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "High" }

      highAction.actionPerformed(TestActionEvent.createTestEvent(highAction))

      assertThat(fixture.view.launchProfileLink.text).isEqualTo("High")
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
      launchSettingsCommand(controller, "Save as Default").onChosen()

      val savedProfile = launcher.preferences.launchProfiles.single()
      assertThat(statusMessage).isEqualTo("Launch profile saved as default.")
      assertThat(savedProfile.name).isEqualTo("High")
      assertThat(savedProfile.generationSettings.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(savedProfile.id)
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
    }
  }

  @Test
  fun inlineUpdateProfileActionUpdatesActiveCustomProfile() {
    runInEdtAndWait {
      val carefulProfile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(carefulProfile),
          defaultLaunchProfileId = carefulProfile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      var statusMessage: String? = null
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        defaultProfileActionControl = fixture.view.defaultProfileActionControl,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { message -> statusMessage = message },
      )
      controller.restoreLaunchProfiles(launcher.preferences)
      val highAction = checkNotNull(controller.createReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "High" }

      highAction.actionPerformed(TestActionEvent.createTestEvent(highAction))

      assertThat(fixture.view.launchProfileLink.text).isEqualTo("High")
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
      launchSettingsCommand(controller, "Update Profile").onChosen()

      val savedProfile = launcher.preferences.launchProfiles.single()
      assertThat(statusMessage).isEqualTo("Launch profile updated.")
      assertThat(savedProfile.id).isEqualTo(carefulProfile.id)
      assertThat(savedProfile.name).isEqualTo(carefulProfile.name)
      assertThat(savedProfile.generationSettings.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.HIGH)
      assertThat(launcher.preferences.defaultLaunchProfileId).isEqualTo(carefulProfile.id)
      assertThat(fixture.view.launchProfileLink.text).isEqualTo("Careful")
      assertThat(fixture.view.defaultProfileActionControl.component.isVisible).isFalse()
    }
  }

  @Test
  fun manageProfilesRowsIncludeBuiltInAndUserProfiles() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:careful",
        name = "Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportedLaunchModesOverride = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        launchProfileLink = fixture.view.launchProfileLink,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )
      controller.restoreLaunchProfiles(launcher.preferences)

      val profiles = controller.manageProfilesRowsForTest()

      assertThat(profiles.map { managedProfile -> managedProfile.name })
        .containsExactly("Codex", "Codex (Full Auto)", "Careful")
      assertThat(profiles.map { managedProfile -> managedProfile.kind })
        .containsExactly(AgentPromptLaunchProfileKind.BUILT_IN, AgentPromptLaunchProfileKind.BUILT_IN, AgentPromptLaunchProfileKind.USER)
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupActionsCloseOnSelection(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.1-codex", displayName = "GPT-5.1 Codex").withGroup(AgentPromptGenerationModelGroup.OPENAI),
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
          onDefaultSaved = { _ -> },
        ).also { controller -> controller.refreshPresentation() }
      }

      withContext(Dispatchers.EDT) {
        controller.createModelActionGroupForTest(loadIfNeeded = true)
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          controller.createModelActionGroupForTest()
            ?.getChildren(TestActionEvent.createTestEvent())
            ?.mapNotNull { action -> action.templatePresentation.text }
            ?.contains("GPT-5.1 Codex") == true
        }
      }
      withContext(Dispatchers.EDT) {
        val actionGroup = checkNotNull(controller.createModelActionGroupForTest())
        val actions = actionGroup.getChildren(TestActionEvent.createTestEvent())

        assertThat(modelActionEntries(actions))
          .containsExactly("model:Default", "model:GPT-5.1 Codex")
        assertThat(actions.filterNot { action -> action is Separator }.map { action -> action.templatePresentation.keepPopupOnPerform })
          .containsOnly(KeepPopupOnPerform.Never)

        val defaultAction = actions.single { action -> action.templatePresentation.text == "Default" }
        val modelAction = actions.single { action -> action.templatePresentation.text == "GPT-5.1 Codex" }
        assertThat(isSelectedInPopup(defaultAction)).isTrue()
        assertThat(isSelectedInPopup(modelAction)).isFalse()

        modelAction.actionPerformed(TestActionEvent.createTestEvent(modelAction))

        val updatedActions = checkNotNull(controller.createModelActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(isSelectedInPopup(updatedActions.single { action -> action.templatePresentation.text == "Default" })).isFalse()
        assertThat(isSelectedInPopup(updatedActions.single { action -> action.templatePresentation.text == "GPT-5.1 Codex" })).isTrue()
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupGroupsModelsBySource(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "claude-opus-4-8", displayName = "Claude Opus")
            .withGroup(AgentPromptGenerationModelGroup.CLAUDE_CODE),
          AgentPromptGenerationModel(id = "custom-model", displayName = "Custom Model"),
          AgentPromptGenerationModel(id = "qwen-local", displayName = "Qwen Local").withGroup(AgentPromptGenerationModelGroup.LOCAL),
          AgentPromptGenerationModel(id = "gpt-5.5", displayName = "GPT-5.5").withGroup(AgentPromptGenerationModelGroup.OPENAI),
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
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        controller.createModelActionGroupForTest(loadIfNeeded = true)
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller)?.contains("Qwen Local") == true
        }
      }
      withContext(Dispatchers.EDT) {
        val actions = checkNotNull(controller.createModelActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(modelActionEntries(actions)).containsExactly(
          "model:Default",
          "separator:Local",
          "model:Qwen Local",
          "separator:OpenAI",
          "model:GPT-5.5",
          "separator:Claude Code",
          "model:Claude Opus",
          "separator:Other",
          "model:Custom Model",
        )
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupStartsCatalogLoadingOnDemand(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"),
        ),
        onListAvailableGenerationModels = modelCatalogRequests::incrementAndGet,
      )
      val fixtureAndController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        val controller = AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        ).also { controller -> controller.refreshPresentation() }
        fixture to controller
      }

      withContext(Dispatchers.EDT) {
        val (fixture, controller) = fixtureAndController
        assertThat(fixture.view.modelSelectorLink.isVisible).isFalse()
        assertThat(modelCatalogRequests.get()).isZero()
        val loadingActions = checkNotNull(controller.createModelActionGroupForTest(loadIfNeeded = true))
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(loadingActions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "Loading models...")
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          fixtureAndController.second.createModelActionGroupForTest()
            ?.getChildren(TestActionEvent.createTestEvent())
            ?.mapNotNull { action -> action.templatePresentation.text }
            ?.contains("ChatGPT 5.5") == true
        }
      }
      withContext(Dispatchers.EDT) {
        val actions = checkNotNull(fixtureAndController.second.createModelActionGroupForTest())
          .getChildren(TestActionEvent.createTestEvent())
        assertThat(modelCatalogRequests.get()).isEqualTo(1)
        assertThat(actions.mapNotNull { action -> action.templatePresentation.text })
          .containsExactly("Default", "ChatGPT 5.5")
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupLoadDeduplicatesInFlightRefresh(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val refreshStarted = CompletableDeferred<Unit>()
    val finishRefresh = CompletableDeferred<Unit>()
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModelsResolver = {
          modelCatalogRequests.incrementAndGet()
          refreshStarted.complete(Unit)
          finishRefresh.await()
          listOf(AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"))
        },
      )
      val controller = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(modelCatalogRequests.get()).isZero()
        assertThat(modelActionTexts(controller, loadIfNeeded = true))
          .containsExactly("Default", "Loading models...")
      }
      waitForCondition { refreshStarted.isCompleted }
      withContext(Dispatchers.EDT) {
        assertThat(modelActionTexts(controller))
          .containsExactly("Default", "Loading models...")
        assertThat(modelCatalogRequests.get()).isEqualTo(1)
      }
      finishRefresh.complete(Unit)
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller) == listOf("Default", "ChatGPT 5.5")
        }
      }
      assertThat(modelCatalogRequests.get()).isEqualTo(1)
    }
    finally {
      if (!finishRefresh.isCompleted) {
        finishRefresh.complete(Unit)
      }
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupLoadUsesSelectedProvider(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val codexCatalogRequests = AtomicInteger()
      val piCatalogRequests = AtomicInteger()
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.1-codex", displayName = "GPT-5.1 Codex"),
        ),
        onListAvailableGenerationModels = codexCatalogRequests::incrementAndGet,
      )
      val piProvider = testProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModels = listOf(
          AgentPromptGenerationModel(id = "gpt-5.5", displayName = "GPT-5.5"),
        ),
        onListAvailableGenerationModels = piCatalogRequests::incrementAndGet,
      )
      val controller = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(codexProvider, piProvider)).also { fixture ->
          fixture.selector.refresh()
          fixture.selector.selectProvider(AgentSessionProvider.from("pi"))
        }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(codexCatalogRequests.get()).isZero()
        assertThat(piCatalogRequests.get()).isZero()
        assertThat(modelActionTexts(controller, loadIfNeeded = true))
          .containsExactly("Default", "Loading models...")
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller) == listOf("Default", "GPT-5.5")
        }
      }
      assertThat(codexCatalogRequests.get()).isZero()
      assertThat(piCatalogRequests.get()).isEqualTo(1)
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupRetriesAfterInitialCatalogFailure(): Unit = timeoutRunBlocking {
    val modelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModelsResolver = {
          when (modelCatalogRequests.incrementAndGet()) {
            1 -> error("failed")
            2 -> listOf(AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"))
            else -> error("Unexpected model catalog refresh")
          }
        },
      )
      val controller = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(modelActionTexts(controller, loadIfNeeded = true))
          .containsExactly("Default", "Loading models...")
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller) == listOf("Default", "Unable to load models", "Retry Loading Models")
        }
      }
      withContext(Dispatchers.EDT) {
        performRetryModelCatalogAction(controller)
        assertThat(modelActionTexts(controller))
          .containsExactly("Default", "Loading models...")
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller) == listOf("Default", "ChatGPT 5.5")
        }
      }
      assertThat(modelCatalogRequests.get()).isEqualTo(2)
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupUsesFreshCachedCatalogOnReopen(): Unit = timeoutRunBlocking {
    val firstModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val secondModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModelsResolver = {
          when (modelCatalogRequests.incrementAndGet()) {
            1 -> listOf(AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"))
            else -> error("Unexpected fresh cached model catalog refresh")
          }
        },
      )
      val firstController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = firstModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        firstController.createModelActionGroupForTest(loadIfNeeded = true)
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(firstController) == listOf("Default", "ChatGPT 5.5")
        }
      }
      firstModelCatalogScope.cancel()

      val secondController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = secondModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(modelActionTexts(secondController, loadIfNeeded = true))
          .containsExactly("Default", "ChatGPT 5.5")
      }
      assertThat(modelCatalogRequests.get()).isEqualTo(1)
    }
    finally {
      firstModelCatalogScope.cancel()
      secondModelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupRefreshesStaleCachedCatalogOnReopen(): Unit = timeoutRunBlocking {
    val firstModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val secondModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val secondRefreshStarted = CompletableDeferred<Unit>()
    val finishSecondRefresh = CompletableDeferred<Unit>()
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModelsResolver = {
          when (modelCatalogRequests.incrementAndGet()) {
            1 -> listOf(AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"))
            else -> {
              secondRefreshStarted.complete(Unit)
              finishSecondRefresh.await()
              listOf(AgentPromptGenerationModel(id = "chatgpt-5.6", displayName = "ChatGPT 5.6"))
            }
          }
        },
      )
      val firstController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = firstModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        firstController.createModelActionGroupForTest(loadIfNeeded = true)
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(firstController) == listOf("Default", "ChatGPT 5.5")
        }
      }
      firstModelCatalogScope.cancel()
      ProjectManager.getInstance().defaultProject.service<AgentPromptGenerationModelCatalogService>()
        .ageCachedCatalogForTest(AgentSessionProvider.from("junie").value, 31.seconds)

      val secondController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = secondModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(modelActionTexts(secondController, loadIfNeeded = true))
          .containsExactly("Default", "ChatGPT 5.5")
      }
      waitForCondition { secondRefreshStarted.isCompleted }
      assertThat(modelCatalogRequests.get()).isEqualTo(2)
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(secondController) == listOf("Default", "ChatGPT 5.5", "Refreshing models...")
        }
      }
      finishSecondRefresh.complete(Unit)
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(secondController) == listOf("Default", "ChatGPT 5.6")
        }
      }
    }
    finally {
      if (!finishSecondRefresh.isCompleted) {
        finishSecondRefresh.complete(Unit)
      }
      firstModelCatalogScope.cancel()
      secondModelCatalogScope.cancel()
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun generationSettingsModelPopupKeepsCachedCatalogWhenBackgroundRefreshFails(): Unit = timeoutRunBlocking {
    val firstModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val secondModelCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    val secondRefreshStarted = CompletableDeferred<Unit>()
    val failSecondRefresh = CompletableDeferred<Unit>()
    try {
      val modelCatalogRequests = AtomicInteger()
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("junie"),
        promptOptions = emptyList(),
        supportsGenerationModelSelection = true,
        availableGenerationModelsResolver = {
          when (modelCatalogRequests.incrementAndGet()) {
            1 -> listOf(AgentPromptGenerationModel(id = "chatgpt-5.5", displayName = "ChatGPT 5.5"))
            else -> {
              secondRefreshStarted.complete(Unit)
              failSecondRefresh.await()
              error("failed")
            }
          }
        },
      )
      val firstController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = firstModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        firstController.createModelActionGroupForTest(loadIfNeeded = true)
      }
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(firstController) == listOf("Default", "ChatGPT 5.5")
        }
      }
      firstModelCatalogScope.cancel()
      ProjectManager.getInstance().defaultProject.service<AgentPromptGenerationModelCatalogService>()
        .ageCachedCatalogForTest(AgentSessionProvider.from("junie").value, 31.seconds)

      val secondController = withContext(Dispatchers.EDT) {
        val fixture = createSelectorFixture(listOf(provider)).also { fixture -> fixture.selector.refresh() }
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = secondModelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        assertThat(modelActionTexts(secondController, loadIfNeeded = true))
          .containsExactly("Default", "ChatGPT 5.5")
      }
      waitForCondition { secondRefreshStarted.isCompleted }
      failSecondRefresh.complete(Unit)
      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(secondController) == listOf("Default", "ChatGPT 5.5", "Unable to refresh models", "Retry Loading Models")
        }
      }
      assertThat(modelCatalogRequests.get()).isEqualTo(2)
    }
    finally {
      if (!failSecondRefresh.isCompleted) {
        failSecondRefresh.complete(Unit)
      }
      firstModelCatalogScope.cancel()
      secondModelCatalogScope.cancel()
    }
  }

  @Test
  fun generationSettingsModelPopupKeepsDefaultForEmptyCatalog(): Unit = timeoutRunBlocking {
    val modelCatalogScope = testScope()
    try {
      val controller = withContext(Dispatchers.EDT) {
        val provider = testProviderBridge(
          provider = AgentSessionProvider.from("junie"),
          promptOptions = emptyList(),
          supportsGenerationModelSelection = true,
        )
        val fixture = createSelectorFixture(listOf(provider))
        fixture.selector.refresh()
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        controller.createModelActionGroupForTest(loadIfNeeded = true)
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          controller.createModelActionGroupForTest()
            ?.getChildren(TestActionEvent.createTestEvent())
            ?.mapNotNull { action -> action.templatePresentation.text } == listOf("Default", "No models available")
        }
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun generationSettingsModelPopupKeepsDefaultWhenCatalogFails(): Unit = timeoutRunBlocking {
    val modelCatalogScope = testScope()
    try {
      val controller = withContext(Dispatchers.EDT) {
        val provider = testProviderBridge(
          provider = AgentSessionProvider.from("junie"),
          promptOptions = emptyList(),
          supportsGenerationModelSelection = true,
          availableGenerationModelsError = IllegalStateException("failed"),
        )
        val fixture = createSelectorFixture(listOf(provider))
        fixture.selector.refresh()
        AgentPromptGenerationSettingsController(
          invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
          providerSelector = fixture.selector,
          generationSettingsPanel = fixture.view.generationSettingsPanel,
          modelSelectorLink = fixture.view.modelSelectorLink,
          reasoningEffortLink = fixture.view.reasoningEffortLink,
          modelCatalogScope = modelCatalogScope,
          launcherProvider = { null },
          onDefaultSaved = { _ -> },
        )
      }

      withContext(Dispatchers.EDT) {
        controller.createModelActionGroupForTest(loadIfNeeded = true)
      }

      waitForCondition {
        withContext(Dispatchers.EDT) {
          modelActionTexts(controller) == listOf(
            "Default",
            "Unable to load models",
            "Retry Loading Models",
          )
        }
      }
    }
    finally {
      modelCatalogScope.cancel()
    }
  }

  @Test
  fun planReasoningEffortAppliesOnlyWhenPlanModeIsSelected() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(
          AgentPromptReasoningEffort.HIGH,
          AgentPromptReasoningEffort.XHIGH,
        ),
        supportsPlanReasoningEffortOverride = true,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        planReasoningEffortLink = fixture.view.planReasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )

      controller.refreshPresentation()
      val extraHighPlanAction = checkNotNull(controller.createPlanReasoningEffortActionGroupForTest())
        .getChildren(TestActionEvent.createTestEvent())
        .single { action -> action.templatePresentation.text == "Extra High" }
      extraHighPlanAction.actionPerformed(TestActionEvent.createTestEvent(extraHighPlanAction))

      assertThat(fixture.view.planReasoningEffortLink.isVisible).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.text).isEqualTo("Plan Effort Extra High")
      assertThat(controller.currentLaunchSettings().planReasoningEffort).isEqualTo(AgentPromptReasoningEffort.XHIGH)

      fixture.selector.setPlanModeSelected(false)
      controller.refreshPresentation()

      assertThat(fixture.view.planReasoningEffortLink.isVisible).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.isEnabled).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.toolTipText)
        .contains(AgentPromptBundle.message("popup.generation.plan.reasoning.disabled.tooltip"))
      assertThat(controller.currentLaunchSettings().planReasoningEffort).isNull()
    }
  }

  @Test
  fun planReasoningEffortStaysHiddenForProviderWithoutPlanEffortSupport() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("claude"),
        promptOptions = listOf(planModeOption()),
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
        planReasoningEffortLink = fixture.view.planReasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { null },
        onDefaultSaved = { _ -> },
      )

      controller.refreshPresentation()

      assertThat(fixture.selector.isPlanModeSelected()).isTrue()
      assertThat(fixture.view.planReasoningEffortLink.isVisible).isFalse()
      assertThat(controller.createPlanReasoningEffortActionGroupForTest()).isNull()
    }
  }

  @Test
  fun launchProfileAppliesPlanEffortWithoutChangingPlanModeSelection() {
    runInEdtAndWait {
      val profile = AgentPromptLaunchProfile(
        id = "user:plan-careful",
        name = "Plan Careful",
        providerId = AgentSessionProvider.from("codex").value,
        generationSettings = AgentPromptGenerationSettings(planReasoningEffort = AgentPromptReasoningEffort.XHIGH),
      )
      val launcher = TestPromptLauncherBridge(
        AgentPromptLauncherBridge.ProviderPreferences(
          launchProfiles = listOf(profile),
          defaultLaunchProfileId = profile.id,
        )
      )
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = listOf(planModeOption()),
        supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH, AgentPromptReasoningEffort.XHIGH),
        supportsPlanReasoningEffortOverride = true,
      )
      val fixture = createSelectorFixture(listOf(provider))
      fixture.selector.refresh()
      fixture.selector.setPlanModeSelected(false)
      val controller = AgentPromptGenerationSettingsController(
        invocationData = testInvocationData(ProjectManager.getInstance().defaultProject),
        providerSelector = fixture.selector,
        generationSettingsPanel = fixture.view.generationSettingsPanel,
        modelSelectorLink = fixture.view.modelSelectorLink,
        reasoningEffortLink = fixture.view.reasoningEffortLink,
        planReasoningEffortLink = fixture.view.planReasoningEffortLink,
        modelCatalogScope = testScope(),
        launcherProvider = { launcher },
        onDefaultSaved = { _ -> },
      )

      controller.restoreLaunchProfiles(launcher.preferences)

      assertThat(fixture.selector.isPlanModeSelected()).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.isVisible).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.isEnabled).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.text).isEqualTo("Plan Effort Extra High")
      assertThat(controller.currentLaunchSettings().planReasoningEffort).isNull()

      fixture.selector.setPlanModeSelected(true)
      controller.refreshPresentation()

      assertThat(fixture.view.planReasoningEffortLink.isVisible).isFalse()
      assertThat(fixture.view.planReasoningEffortLink.isEnabled).isTrue()
      assertThat(controller.currentLaunchSettings().planReasoningEffort).isEqualTo(AgentPromptReasoningEffort.XHIGH)
    }
  }

  @Test
  fun chooserGroupAndProviderActionsAreDumbAware() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
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
        provider = AgentSessionProvider.from("codex"),
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
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val terminalProvider = testProviderBridge(
        provider = AgentSessionProvider.from("terminal"),
        promptOptions = emptyList(),
        supportsPromptLaunch = false,
      )
      val fixture = createSelectorFixture(listOf(terminalProvider, codexProvider))

      fixture.selector.refresh()

      assertThat(fixture.selector.availableProviders).containsExactly(AgentSessionProvider.from("codex"))
      assertThat(fixture.selector.selectedProvider?.bridge?.provider?.value).isEqualTo(AgentSessionProvider.from("codex").value)
      val actions = checkNotNull(fixture.selector.buildChooserActionGroup { error("should not select provider during filtering test") })
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.map { action -> action.templatePresentation.text }).containsExactly("Codex")
    }
  }

  @Test
  fun promptSelectorHidesUnavailableDiscoverableProviders() {
    runInEdtAndWait {
      val codexProvider = testProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        promptOptions = emptyList(),
      )
      val piProvider = testProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        promptOptions = emptyList(),
        cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
      )
      val fixture = createSelectorFixture(
        providers = listOf(codexProvider, piProvider),
        availabilityByProvider = mapOf(
          AgentSessionProvider.from("codex") to true,
          AgentSessionProvider.from("pi") to false,
        ),
      )

      fixture.selector.refresh()

      assertThat(fixture.selector.availableProviders).containsExactly(AgentSessionProvider.from("codex"))
      val actions = checkNotNull(fixture.selector.buildChooserActionGroup { error("should not select provider during filtering test") })
        .getChildren(TestActionEvent.createTestEvent())
      assertThat(actions.map { action -> action.templatePresentation.text }).containsExactly("Codex")
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun asyncRefreshAppliesResolvedProviderAvailabilityFromUiScope() = timeoutRunBlocking {
    val refreshStarted = CompletableDeferred<Unit>()
    val finishRefresh = CompletableDeferred<Unit>()
    val provider = testProviderBridge(
      provider = AgentSessionProvider.from("codex"),
      promptOptions = emptyList(),
      cliAvailable = false,
      cliAvailableResolver = {
        refreshStarted.complete(Unit)
        finishRefresh.await()
        false
      },
    )
    val asyncRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    try {
      val fixture = withContext(Dispatchers.EDT) {
        createSelectorFixture(listOf(provider), asyncRefreshScope = asyncRefreshScope).also { fixture ->
          fixture.selector.refresh()
        }
      }

      refreshStarted.await()
      assertThat(withContext(Dispatchers.EDT) { fixture.selector.selectedProvider?.isCliAvailable }).isTrue()
      finishRefresh.complete(Unit)
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
    onProviderSelectionChanged: () -> Unit = {},
  ): ProviderSelectorFixture {
    val project = ProjectManager.getInstance().defaultProject
    project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(availabilityByProvider)
    val view = createAgentPromptPaletteView(
      promptArea = EditorTextField(),
      contextChipsPanel = JPanel(),
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
        headerControls = view.headerControls,
        providersProvider = { providers },
        sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptProviderSelector::class.java.classLoader),
        asyncRefreshScope = asyncRefreshScope,
        onProviderSelectionChanged = onProviderSelectionChanged,
      ),
      view = view,
    )
  }

  private fun syncContainerModeForTest(
    fixture: ProviderSelectorFixture,
    requestedSelection: Boolean = fixture.view.containerModeAction.selected,
  ) {
    val state = resolveContainerModeOptionState(
      selectedProvider = fixture.selector.selectedProvider?.bridge?.provider,
      isExtensionTab = false,
      requestedSelection = requestedSelection,
      supportsContainerMode = { provider -> provider == AgentSessionProvider.from("claude") },
      isContainerRuntimeAvailable = { provider -> provider == AgentSessionProvider.from("claude") },
    )
    fixture.view.headerControls.setContainerModeState(
      visible = state.visible,
      enabled = state.enabled,
      selected = state.selected,
      tooltipText = null,
    )
  }

  private fun createLaunchProfileEditorForTest(
    profiles: List<AgentPromptLaunchProfile>,
    activeProfileId: String? = null,
    defaultProfileId: String? = null,
    supportedLaunchModes: Set<AgentSessionLaunchMode> = setOf(AgentSessionLaunchMode.STANDARD),
    supportedReasoningEfforts: Set<AgentPromptReasoningEffort> = emptySet(),
    modelCatalog: List<AgentPromptGenerationModel> = emptyList(),
    providerOverride: AgentSessionProviderDescriptor? = null,
    providerOverrides: List<AgentSessionProviderDescriptor>? = null,
    modelCatalogStateProvider: (String) -> AgentPromptGenerationModelCatalogState? = { providerId ->
      modelCatalog.takeIf { providerId == AgentSessionProvider.from("codex").value && it.isNotEmpty() }
        ?.let(AgentPromptGenerationModelCatalogState::Loaded)
    },
    requestModelCatalogRefresh: (String, () -> Unit) -> Unit = { _, _ -> },
    onCreateProfile: (AgentPromptLaunchProfile) -> Unit = {},
    onUpdateProfile: (AgentPromptLaunchProfile) -> Unit = {},
    onDeleteProfile: (AgentPromptLaunchProfile) -> Boolean = { true },
  ): AgentPromptLaunchProfileEditorDialog {
    val provider = providerOverride ?: testProviderBridge(
      provider = AgentSessionProvider.from("codex"),
      promptOptions = emptyList(),
      supportedLaunchModesOverride = supportedLaunchModes,
      supportedReasoningEffortsOverride = supportedReasoningEfforts,
    )
    val providers = providerOverrides ?: listOf(provider)
    return AgentPromptLaunchProfileEditorDialog(
      project = ProjectManager.getInstance().defaultProject,
      profiles = profiles,
      activeProfileId = activeProfileId,
      defaultProfileId = defaultProfileId,
      builtInProfiles = profiles.filter { profile -> profile.kind == AgentPromptLaunchProfileKind.BUILT_IN },
      providerEntries = providers.map { item ->
        ProviderEntry(item,
                      item.provider.value.replaceFirstChar(Char::uppercase),
                      true,
                      EmptyIcon.ICON_16)
      },
      modelCatalogProvider = { modelCatalog },
      modelCatalogStateProvider = modelCatalogStateProvider,
      requestModelCatalogRefresh = requestModelCatalogRefresh,
      newUserProfileId = { "user:new" },
      onCreateProfile = onCreateProfile,
      onUpdateProfile = onUpdateProfile,
      onDeleteProfile = onDeleteProfile,
      onSetDefaultProfile = {},
      onSelectProfile = {},
    )
  }

  private fun AgentPromptLaunchProfileEditorDialog.closeForTest() {
    close(DialogWrapper.CANCEL_EXIT_CODE)
    disposeIfNeeded()
  }

  private suspend fun waitForCondition(condition: suspend () -> Boolean) {
    withTimeout(5.seconds) {
      while (!condition()) {
        delay(10.milliseconds)
      }
    }
  }

  private fun modelActionTexts(controller: AgentPromptGenerationSettingsController, loadIfNeeded: Boolean = false): List<String>? {
    return controller.createModelActionGroupForTest(loadIfNeeded = loadIfNeeded)
      ?.getChildren(TestActionEvent.createTestEvent())
      ?.filterNot { action -> action is Separator }
      ?.mapNotNull { action -> action.templatePresentation.text }
  }

  private fun launchSettingsCommand(
    controller: AgentPromptGenerationSettingsController,
    text: String,
  ): AgentPromptPopupRow.Command {
    return controller.createLaunchSettingsPopupRowsForTest()
      .filterIsInstance<AgentPromptPopupRow.Command>()
      .single { row -> row.text == text }
  }

  private fun launchSettingsModelSubmenu(controller: AgentPromptGenerationSettingsController): AgentPromptPopupRow.Command {
    return controller.createLaunchSettingsPopupRowsForTest()
      .filterIsInstance<AgentPromptPopupRow.Command>()
      .single { row -> row.subRows.isNotEmpty() }
  }

  private fun launchSettingsWorkbenchModelSubmenu(controller: AgentPromptGenerationSettingsController): AgentWorkbenchPopupRow {
    return controller.createLaunchSettingsWorkbenchPopupRowsForTest().single { row -> row.subRows.isNotEmpty() }
  }

  private fun popupCommand(rows: List<AgentPromptPopupRow>, text: String): AgentPromptPopupRow.Command {
    return rows.filterIsInstance<AgentPromptPopupRow.Command>().single { row -> row.text == text }
  }

  private fun popupRowEntries(rows: List<AgentPromptPopupRow>): List<String> {
    return rows.flatMap { row ->
      buildList {
        row.separatorText?.let { separatorText -> add("separator:$separatorText") }
        add("row:${row.text}")
      }
    }
  }

  private fun modelActionEntries(actions: Array<AnAction>): List<String> {
    return actions.map { action ->
      if (action is Separator) "separator:${action.text.orEmpty()}" else "model:${action.templatePresentation.text.orEmpty()}"
    }
  }

  private fun popupEvent(action: AnAction): AnActionEvent {
    return AnActionEvent.createEvent(
      action,
      DataContext.EMPTY_CONTEXT,
      null,
      "",
      ActionUiKind.POPUP,
      null,
    )
  }

  private fun isSelectedInPopup(action: AnAction): Boolean {
    val event = popupEvent(action)
    action.update(event)
    return Toggleable.isSelected(event.presentation)
  }

  private fun performRetryModelCatalogAction(controller: AgentPromptGenerationSettingsController) {
    val action = checkNotNull(
      controller.createModelActionGroupForTest()
        ?.getChildren(TestActionEvent.createTestEvent())
        ?.singleOrNull { action -> action.templatePresentation.text == "Retry Loading Models" }
    )
    action.actionPerformed(TestActionEvent.createTestEvent(action))
  }

  private fun <T> withNoTestDialog(action: () -> T): T {
    val previous = TestDialogManager.setTestDialog { Messages.NO }
    try {
      return action()
    }
    finally {
      TestDialogManager.setTestDialog(previous)
    }
  }

  private fun testProviderBridge(
    provider: AgentSessionProvider,
    promptOptions: List<AgentPromptProviderOption>,
    cliAvailable: Boolean = true,
    supportsPromptLaunch: Boolean = true,
    cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.PROMINENT,
    supportedLaunchModesOverride: Set<AgentSessionLaunchMode> = setOf(AgentSessionLaunchMode.STANDARD),
    supportedReasoningEffortsOverride: Set<AgentPromptReasoningEffort> = emptySet(),
    supportsPlanReasoningEffortOverride: Boolean = false,
    availableGenerationModels: List<AgentPromptGenerationModel> = emptyList(),
    supportsGenerationModelSelection: Boolean = availableGenerationModels.isNotEmpty(),
    availableGenerationModelsError: Throwable? = null,
    availableGenerationModelsResolver: suspend () -> List<AgentPromptGenerationModel> = { availableGenerationModels },
    onListAvailableGenerationModels: () -> Unit = {},
    displayNameForGenerationModelId: (String) -> String? = { null },
    icon: Icon = EmptyIcon.ICON_16,
    monochromeIconOverride: Icon? = null,
    cliAvailableResolver: suspend () -> Boolean = { cliAvailable },
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = cliVisibilityPolicy
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = "toolwindow.action.new.session.${provider.value}"
      override val yoloSessionLabelKey: String = "toolwindow.action.new.session.${provider.value}.yolo"
      override val yoloSessionModeLabelKey: String = "toolwindow.action.new.session.${provider.value}.yolo.mode"
      override val promptOptions: List<AgentPromptProviderOption> = promptOptions
      override val supportedLaunchModes: Set<AgentSessionLaunchMode> = supportedLaunchModesOverride
      override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort> = supportedReasoningEffortsOverride
      override val supportsPlanReasoningEffort: Boolean = supportsPlanReasoningEffortOverride
      override val supportsGenerationModelSelection: Boolean = supportsGenerationModelSelection
      override val supportsPromptLaunch: Boolean = supportsPromptLaunch
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = icon
      override val monochromeIcon: Icon = monochromeIconOverride ?: icon

      override suspend fun isCliAvailable(): Boolean = cliAvailableResolver()

      override suspend fun listAvailableGenerationModels(project: com.intellij.openapi.project.Project?): List<AgentPromptGenerationModel> {
        onListAvailableGenerationModels()
        availableGenerationModelsError?.let { throw it }
        return availableGenerationModelsResolver()
      }

      override fun displayNameForGenerationModelId(modelId: String): String? {
        return displayNameForGenerationModelId.invoke(modelId)
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

  private fun planModeOption(defaultSelected: Boolean = true): AgentPromptProviderOption {
    return AgentPromptProviderOption(
      id = AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE,
      labelKey = "toolwindow.prompt.option.plan.mode",
      labelFallback = "Plan mode",
      defaultSelected = defaultSelected,
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

    override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
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
