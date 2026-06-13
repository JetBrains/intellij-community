// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptContainerModeDecisionsTest {
  @Test
  fun containerModeIsShownOnlyForSupportedProvidersOutsideExtensionTabs() {
    val supportsClaude: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }

    assertThat(
      shouldShowContainerModeOption(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
      )
    ).isTrue()

    assertThat(
      shouldShowContainerModeOption(
        selectedProvider = AgentSessionProvider.CODEX,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
      )
    ).isFalse()

    assertThat(
      shouldShowContainerModeOption(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = true,
        supportsContainerMode = supportsClaude,
      )
    ).isFalse()
  }

  @Test
  fun containerModeIsEnabledOnlyWhenRuntimeIsAvailable() {
    val supportsClaude: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeAvailable: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeUnavailable: (AgentSessionProvider) -> Boolean = { false }

    assertThat(
      shouldEnableContainerModeOption(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isTrue()

    assertThat(
      shouldEnableContainerModeOption(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeUnavailable,
      )
    ).isFalse()

    assertThat(
      shouldEnableContainerModeOption(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = true,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isFalse()
  }

  @Test
  fun containerModeSubmitRequiresSelectionAndAvailability() {
    val supportsClaude: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeAvailable: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeUnavailable: (AgentSessionProvider) -> Boolean = { false }

    assertThat(
      shouldSubmitContainerMode(
        isSelected = true,
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isTrue()

    assertThat(
      shouldSubmitContainerMode(
        isSelected = false,
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isFalse()

    assertThat(
      shouldSubmitContainerMode(
        isSelected = true,
        selectedProvider = AgentSessionProvider.CODEX,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isFalse()

    assertThat(
      shouldSubmitContainerMode(
        isSelected = true,
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeUnavailable,
      )
    ).isFalse()
  }

  @Test
  fun containerModeStateClampsRestoredSelectionToEligibility() {
    val supportsClaude: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeAvailable: (AgentSessionProvider) -> Boolean = { provider -> provider == AgentSessionProvider.CLAUDE }
    val runtimeUnavailable: (AgentSessionProvider) -> Boolean = { false }

    assertThat(
      resolveContainerModeOptionState(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        requestedSelection = true,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isEqualTo(
      ContainerModeOptionState(
        visible = true,
        enabled = true,
        selected = true,
        showUnavailableTooltip = false,
      )
    )

    assertThat(
      resolveContainerModeOptionState(
        selectedProvider = AgentSessionProvider.CODEX,
        isExtensionTab = false,
        requestedSelection = true,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isEqualTo(
      ContainerModeOptionState(
        visible = false,
        enabled = false,
        selected = false,
        showUnavailableTooltip = false,
      )
    )

    assertThat(
      resolveContainerModeOptionState(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = false,
        requestedSelection = true,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeUnavailable,
      )
    ).isEqualTo(
      ContainerModeOptionState(
        visible = true,
        enabled = false,
        selected = false,
        showUnavailableTooltip = true,
      )
    )

    assertThat(
      resolveContainerModeOptionState(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isExtensionTab = true,
        requestedSelection = true,
        supportsContainerMode = supportsClaude,
        isContainerRuntimeAvailable = runtimeAvailable,
      )
    ).isEqualTo(
      ContainerModeOptionState(
        visible = false,
        enabled = false,
        selected = false,
        showUnavailableTooltip = false,
      )
    )
  }
}
