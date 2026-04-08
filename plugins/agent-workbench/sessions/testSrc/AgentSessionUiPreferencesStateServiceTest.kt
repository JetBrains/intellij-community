package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionUiPreferencesStateServiceTest {
  @Test
  fun lastUsedProviderDefaultsToNull() {
    val preferences = AgentSessionUiPreferencesStateService()
    assertThat(preferences.getLastUsedProvider()).isNull()
    assertThat(preferences.lastUsedProviderFlow.value).isNull()
  }

  @Test
  fun setAndGetLastUsedProvider() {
    val preferences = AgentSessionUiPreferencesStateService()

    preferences.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CLAUDE)

    preferences.setLastUsedProvider(AgentSessionProvider.CODEX)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun getAndSetProviderPreferencesRoundTrip() {
    val service = AgentSessionUiPreferencesStateService()

    val prefs = AgentPromptLauncherBridge.ProviderPreferences(
      providerId = AgentSessionProvider.CLAUDE.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
    )
    service.setProviderPreferences(prefs)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerId).isEqualTo(AgentSessionProvider.CLAUDE.value)
    assertThat(loaded.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
  }

  @Test
  fun setProviderPreferencesUpdatesLastUsedProviderFlow() {
    val service = AgentSessionUiPreferencesStateService()

    service.setProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerId = AgentSessionProvider.CODEX.value,
        launchMode = AgentSessionLaunchMode.STANDARD,
      )
    )

    assertThat(service.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(service.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun lastUsedLaunchModeDefaultsToNull() {
    val service = AgentSessionUiPreferencesStateService()
    assertThat(service.getLastUsedLaunchMode()).isNull()
  }

  @Test
  fun getLastUsedLaunchModeReturnsYoloAfterSettingYoloPreference() {
    val service = AgentSessionUiPreferencesStateService()
    service.setProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerId = AgentSessionProvider.CODEX.value,
        launchMode = AgentSessionLaunchMode.YOLO,
      )
    )
    assertThat(service.getLastUsedLaunchMode()).isEqualTo(AgentSessionLaunchMode.YOLO)
  }

  @Test
  fun getLastUsedLaunchModeReturnsNullForUnknownModeName() {
    val service = AgentSessionUiPreferencesStateService()
    // Default state has no launch mode
    service.loadState(AgentSessionUiPreferencesStateService.UiPreferencesState())
    assertThat(service.getLastUsedLaunchMode()).isNull()
  }

  @Test
  fun lastUsedVcsMergePreferencesDefaultToNull() {
    val service = AgentSessionUiPreferencesStateService()

    assertThat(service.getLastUsedVcsMergeProvider()).isNull()
    assertThat(service.getLastUsedVcsMergeLaunchMode()).isNull()
  }

  @Test
  fun updateProviderPreferencesOnLaunchPersistsAllFields() {
    val service = AgentSessionUiPreferencesStateService()
    val request = AgentPromptInitialMessageRequest(
      prompt = "fix bug",
      providerOptionIds = setOf("plan_mode"),
    )

    service.updateProviderPreferencesOnLaunch(AgentSessionProvider.CODEX, AgentSessionLaunchMode.YOLO, request)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerId).isEqualTo(AgentSessionProvider.CODEX.value)
    assertThat(loaded.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("codex" to setOf("plan_mode")))
    assertThat(service.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun updateProviderPreferencesOnLaunchMergesProviderOptions() {
    val service = AgentSessionUiPreferencesStateService()
    // Pre-populate options for claude
    service.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerId = AgentSessionProvider.CLAUDE.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
    ))

    // Launch with codex — should merge, not replace
    service.updateProviderPreferencesOnLaunch(
      AgentSessionProvider.CODEX,
      AgentSessionLaunchMode.YOLO,
      AgentPromptInitialMessageRequest(prompt = "test", providerOptionIds = setOf("fast")),
    )

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).containsEntry("claude", setOf("plan_mode"))
    assertThat(loaded.providerOptionsByProviderId).containsEntry("codex", setOf("fast"))
  }

  @Test
  fun updateProviderPreferencesOnLaunchWithNullRequestPreservesExistingOptions() {
    val service = AgentSessionUiPreferencesStateService()
    service.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
    ))

    service.updateProviderPreferencesOnLaunch(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD, null)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerId).isEqualTo(AgentSessionProvider.CODEX.value)
    assertThat(loaded.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
  }

  @Test
  fun updateVcsMergeProviderPreferencesOnLaunchDoesNotOverwriteGeneralDefaults() {
    val service = AgentSessionUiPreferencesStateService()
    service.setProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerId = AgentSessionProvider.CLAUDE.value,
        launchMode = AgentSessionLaunchMode.YOLO,
        providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
      )
    )

    service.updateVcsMergeProviderPreferencesOnLaunch(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD)

    val generalPreferences = service.getProviderPreferences()
    assertThat(generalPreferences.providerId).isEqualTo(AgentSessionProvider.CLAUDE.value)
    assertThat(generalPreferences.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(generalPreferences.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
    assertThat(service.getLastUsedVcsMergeProvider()).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(service.getLastUsedVcsMergeLaunchMode()).isEqualTo(AgentSessionLaunchMode.STANDARD)
  }
}
