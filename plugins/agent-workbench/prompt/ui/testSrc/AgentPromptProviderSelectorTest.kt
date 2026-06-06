// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
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
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = cliVisibilityPolicy
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = displayNameKey
      override val promptOptions: List<AgentPromptProviderOption> = promptOptions
      override val supportsPromptLaunch: Boolean = supportsPromptLaunch
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16

      override suspend fun isCliAvailable(): Boolean = cliAvailable

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
