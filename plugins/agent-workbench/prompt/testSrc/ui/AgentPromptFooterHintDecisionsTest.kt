// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

class AgentPromptFooterHintDecisionsTest {
  @Test
  fun codexExistingModeUsesCodexFooterHint() {
    val selectedProvider = testPromptProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportsTabQueueShortcut = true,
    )
    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = selectedProvider,
      )
    ).isEqualTo("popup.footer.hint.existing.queue")
  }

  @Test
  fun existingTaskWithNextPromptTabUsesDefaultFooterHintEvenForCodex() {
    val selectedProvider = testPromptProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportsTabQueueShortcut = true,
    )
    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = selectedProvider,
        hasNextPromptTab = true,
      )
    ).isEqualTo("popup.footer.hint")
  }

  @Test
  fun nonCodexOrNonExistingModeUsesDefaultFooterHint() {
    val codexProvider = testPromptProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportsTabQueueShortcut = true,
    )
    val claudeProvider = testPromptProviderBridge(provider = AgentSessionProvider.CLAUDE)
    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.NEW_TASK,
        selectedProvider = codexProvider,
      )
    ).isEqualTo("popup.footer.hint")

    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = claudeProvider,
      )
    ).isEqualTo("popup.footer.hint")

    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = null,
      )
    ).isEqualTo("popup.footer.hint")
  }

  @Test
  fun existingTaskSelectionHintIsHiddenForCodexExistingMode() {
    val selectedProvider = testPromptProviderBridge(
      provider = AgentSessionProvider.CODEX,
      suppressExistingTaskSelectionHint = true,
    )
    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = null,
        selectedProvider = selectedProvider,
      )
    ).isFalse()
  }

  @Test
  fun existingTaskSelectionHintRequiresNoSelectionInExistingNonCodexMode() {
    val selectedProvider = testPromptProviderBridge(provider = AgentSessionProvider.CLAUDE)
    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = null,
        selectedProvider = selectedProvider,
      )
    ).isTrue()

    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = "thread-1",
        selectedProvider = selectedProvider,
      )
    ).isFalse()

    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.NEW_TASK,
        selectedExistingTaskId = null,
        selectedProvider = selectedProvider,
      )
    ).isFalse()
  }
}

private fun testPromptProviderBridge(
  provider: AgentSessionProvider,
  supportsTabQueueShortcut: Boolean = false,
  suppressExistingTaskSelectionHint: Boolean = false,
): AgentSessionProviderDescriptor {
  return object : AgentSessionProviderDescriptor {
    override val provider: AgentSessionProvider = provider
    override val displayNameKey: String = provider.value
    override val newSessionLabelKey: String = provider.value
    override val icon: Icon
      get() = error("Not required for this test")
    override val sessionSource: AgentSessionSource
      get() = error("Not required for this test")
    override val cliMissingMessageKey: String = provider.value
    override val supportsPromptTabQueueShortcut: Boolean = supportsTabQueueShortcut
    override val suppressPromptExistingTaskSelectionHint: Boolean = suppressExistingTaskSelectionHint

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
      return AgentSessionLaunchSpec(sessionId = null, launchSpec = AgentSessionTerminalLaunchSpec(command = emptyList()))
    }

    override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
      return AgentInitialMessagePlan.EMPTY
    }
  }
}
