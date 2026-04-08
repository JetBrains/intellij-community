// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

class AgentPromptPlanModeDecisionsTest {
    @Test
    fun newTaskKeepsSelectedPlanModeOption() {
        val selectedProvider = testPlanModeProviderBridge()
        val effectiveOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            targetMode = PromptTargetMode.NEW_TASK,
            selectedThreadActivity = null,
        )

        assertThat(effectiveOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      }

    @Test
    fun existingReadyTaskKeepsSelectedPlanModeOption() {
        val selectedProvider = testPlanModeProviderBridge()
        val effectiveOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            targetMode = PromptTargetMode.EXISTING_TASK,
            selectedThreadActivity = AgentThreadActivity.READY,
        )

        assertThat(effectiveOptionIds).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
      }

    @Test
    fun activeExistingTaskClearsPlanModeOption() {
        val selectedProvider = testPlanModeProviderBridge()

        val processingOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            targetMode = PromptTargetMode.EXISTING_TASK,
            selectedThreadActivity = AgentThreadActivity.PROCESSING,
        )
        assertThat(processingOptionIds).isEmpty()

        val reviewingOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            targetMode = PromptTargetMode.EXISTING_TASK,
            selectedThreadActivity = AgentThreadActivity.REVIEWING,
        )
        assertThat(reviewingOptionIds).isEmpty()
      }

    @Test
    fun providerWithoutPlanModeOptionNeverEnablesPlanMode() {
        val selectedProvider = testPlanModeProviderBridge(
            promptOptions = emptyList(),
        )
        val effectiveOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            targetMode = PromptTargetMode.NEW_TASK,
            selectedThreadActivity = null,
        )

        assertThat(effectiveOptionIds).isEmpty()
      }

    @Test
    fun deselectedPlanModeOptionNeverEnablesPlanMode() {
        val selectedProvider = testPlanModeProviderBridge()
        val effectiveOptionIds = resolveEffectiveProviderOptionIds(
            selectedProvider = selectedProvider,
            selectedOptionIds = emptySet(),
            targetMode = PromptTargetMode.NEW_TASK,
            selectedThreadActivity = null,
        )

        assertThat(effectiveOptionIds).isEmpty()
      }
}

private fun testPlanModeProviderBridge(
    provider: AgentSessionProvider = AgentSessionProvider.CODEX,
    promptOptions: List<AgentPromptProviderOption> = listOf(PLAN_MODE_OPTION),
): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
        override val provider: AgentSessionProvider = provider
        override val displayNameKey: String = provider.value
        override val newSessionLabelKey: String = provider.value
        override val icon: Icon
            get() = error("Not required for this test")
        override val promptOptions: List<AgentPromptProviderOption> = promptOptions
        override val sessionSource: AgentSessionSource
            get() = error("Not required for this test")
        override val cliMissingMessageKey: String = provider.value

        override fun isCliAvailable(): Boolean = true

        override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
            return AgentSessionTerminalLaunchSpec(command = emptyList())
        }

        override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
            return AgentSessionTerminalLaunchSpec(command = emptyList())
        }

        override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
            return AgentInitialMessagePlan.EMPTY
        }
    }
}

private val PLAN_MODE_OPTION = AgentPromptProviderOption(
    id = AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE,
    labelKey = "toolwindow.prompt.option.plan.mode",
    labelFallback = "Plan mode",
    defaultSelected = true,
    disabledExistingTaskActivities = setOf(AgentThreadActivity.PROCESSING, AgentThreadActivity.REVIEWING),
)
