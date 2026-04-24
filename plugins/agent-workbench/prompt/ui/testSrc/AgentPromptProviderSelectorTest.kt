// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import javax.swing.JPanel

@TestApplication
class AgentPromptProviderSelectorTest {
  @Test
  fun planModeCheckboxUsesMnemonicAndUpdatesStoredSelection() {
    runInEdtAndWait {
      val provider = testProviderBridge(
        provider = AgentSessionProvider.CODEX,
        supportsPlanMode = true,
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
        supportsPlanMode = false,
        promptOptions = emptyList(),
      )
      val fixture = createSelectorFixture(listOf(provider))

      fixture.selector.refresh()

      assertThat(fixture.providerOptionsPanel.componentCount).isZero()
      assertThat(fixture.providerOptionsPanel.isVisible).isFalse()
    }
  }

  private fun createSelectorFixture(providers: List<AgentSessionProviderDescriptor>): ProviderSelectorFixture {
    val project = ProjectManager.getInstance().defaultProject
    val providerOptionsPanel = JPanel()
    return ProviderSelectorFixture(
      selector = AgentPromptProviderSelector(
        invocationData = AgentPromptInvocationData(
          project = project,
          actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
          actionText = "Ask Agent",
          actionPlace = "MainMenu",
          invokedAtMs = 0L,
        ),
        providerIconLabel = JBLabel(),
        providerOptionsPanel = providerOptionsPanel,
        providersProvider = { providers },
        sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptProviderSelector::class.java.classLoader),
      ),
      providerOptionsPanel = providerOptionsPanel,
    )
  }

  private fun testProviderBridge(
    provider: AgentSessionProvider,
    supportsPlanMode: Boolean,
    promptOptions: List<AgentPromptProviderOption>,
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = displayNameKey
      override val supportsPlanMode: Boolean = supportsPlanMode
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

      override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
        return AgentSessionLaunchSpec(
          sessionId = null,
          launchSpec = AgentSessionTerminalLaunchSpec(command = emptyList()),
        )
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
    val providerOptionsPanel: JPanel,
  ) {
    fun planModeCheckBox(): JBCheckBox {
      return providerOptionsPanel.components.single() as JBCheckBox
    }
  }
}
