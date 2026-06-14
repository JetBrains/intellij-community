package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionUiPreferencesStateServiceTest {
  @Test
  fun lastUsedProviderDefaultsToNull() {
    val preferences = uiPreferencesService()
    assertThat(preferences.getLastUsedProvider()).isNull()
    assertThat(preferences.lastUsedProviderFlow.value).isNull()
  }

  @Test
  fun setAndGetLastUsedProvider() {
    val preferences = uiPreferencesService()

    preferences.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CLAUDE)

    preferences.setLastUsedProvider(AgentSessionProvider.CODEX)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun getAndSetProviderPreferencesRoundTrip() {
    val service = uiPreferencesService()

    val prefs = AgentPromptLauncherBridge.ProviderPreferences(
      providerId = AgentSessionProvider.CLAUDE.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
      launchProfiles = listOf(launchProfile("user:careful", "Careful", AgentSessionProvider.CLAUDE.value)),
      activeLaunchProfileId = "user:careful",
    )
    service.setProviderPreferences(prefs)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerId).isEqualTo(AgentSessionProvider.CLAUDE.value)
    assertThat(loaded.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
    assertThat(loaded.launchProfiles.map(AgentPromptLaunchProfile::id)).containsExactly("user:careful")
    assertThat(loaded.activeLaunchProfileId).isEqualTo("user:careful")
  }

  @Test
  fun setProviderPreferencesUpdatesLastUsedProviderFlow() {
    val service = uiPreferencesService()

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
    val service = uiPreferencesService()
    assertThat(service.getLastUsedLaunchMode()).isNull()
  }

  @Test
  fun getLastUsedLaunchModeReturnsYoloAfterSettingYoloPreference() {
    val service = uiPreferencesService()
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
    val service = uiPreferencesService()
    // Default state has no launch mode
    service.loadState(AgentSessionUiPreferencesStateService.UiPreferencesState())
    assertThat(service.getLastUsedLaunchMode()).isNull()
  }

  @Test
  fun lastUsedVcsMergePreferencesDefaultToNull() {
    val service = uiPreferencesService()

    assertThat(service.getLastUsedVcsMergeProvider()).isNull()
    assertThat(service.getLastUsedVcsMergeLaunchMode()).isNull()
  }

  @Test
  fun updateProviderPreferencesOnLaunchPersistsAllFields() {
    val service = uiPreferencesService()
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
    val service = uiPreferencesService()
    // Pre-populate options for claude
    service.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerId = AgentSessionProvider.CLAUDE.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
      launchProfiles = listOf(launchProfile("user:claude", "Claude Custom", AgentSessionProvider.CLAUDE.value)),
      activeLaunchProfileId = "user:claude",
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
    assertThat(loaded.launchProfiles.map(AgentPromptLaunchProfile::id)).containsExactly("user:claude")
    assertThat(loaded.activeLaunchProfileId).isEqualTo("user:claude")
  }

  @Test
  fun loadStateKeepsProviderOptionsSeparateFromLaunchProfiles() {
    val service = uiPreferencesService()

    service.loadState(AgentSessionUiPreferencesStateService.UiPreferencesState(
      lastUsedProvider = AgentSessionProvider.CODEX.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf(
        AgentSessionProvider.CODEX.value to emptySet(),
        AgentSessionProvider.CLAUDE.value to setOf("plan_mode"),
      ),
    ))

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf(
      AgentSessionProvider.CODEX.value to emptySet(),
      AgentSessionProvider.CLAUDE.value to setOf("plan_mode"),
    ))
    assertThat(loaded.launchProfiles).isEmpty()
    assertThat(loaded.activeLaunchProfileId).isNull()
  }

  @Test
  fun updateProviderPreferencesOnLaunchWithNullRequestPreservesExistingOptions() {
    val service = uiPreferencesService()
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
    val service = uiPreferencesService()
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

  @Test
  fun launchProfilesAreStoredInDedicatedStateService() {
    val launchProfileStateService = AgentSessionLaunchProfileStateService()
    val uiPreferencesService = AgentSessionUiPreferencesStateService(launchProfileStateService)

    uiPreferencesService.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      launchProfiles = listOf(launchProfile("user:fast", "Fast", AgentSessionProvider.CODEX.value)),
      activeLaunchProfileId = "user:fast",
    ))

    assertThat(uiPreferencesService.state).isEqualTo(AgentSessionUiPreferencesStateService.UiPreferencesState())
    assertThat(launchProfileStateService.state.launchProfiles.map(AgentPromptLaunchProfile::id))
      .containsExactly("user:fast")
    assertThat(launchProfileStateService.state.activeLaunchProfileId).isEqualTo("user:fast")
  }

  @Test
  fun launchProfileStateServiceRoundTrip() {
    val service = AgentSessionLaunchProfileStateService()

    service.setLaunchProfiles(
      profiles = listOf(launchProfile("user:careful", "Careful", AgentSessionProvider.CLAUDE.value)),
      activeProfileId = "user:careful",
    )

    assertThat(service.getUserLaunchProfiles().map(AgentPromptLaunchProfile::id))
      .containsExactly("user:careful")
    assertThat(service.getActiveLaunchProfileId()).isEqualTo("user:careful")
  }

  @Test
  fun launchProfileStateServiceStoresBuiltInOverrides() {
    val service = AgentSessionLaunchProfileStateService()
    val profile = launchProfile("builtin:codex:standard", "Careful Codex", AgentSessionProvider.CODEX.value)

    service.setLaunchProfiles(
      profiles = listOf(profile),
      activeProfileId = profile.id,
    )

    assertThat(service.getUserLaunchProfiles()).containsExactly(profile)
    assertThat(service.getActiveLaunchProfileId()).isEqualTo(profile.id)
  }

  private fun uiPreferencesService(): AgentSessionUiPreferencesStateService {
    return AgentSessionUiPreferencesStateService(AgentSessionLaunchProfileStateService())
  }

  private fun launchProfile(id: String, name: String, providerId: String): AgentPromptLaunchProfile {
    return AgentPromptLaunchProfile(
      id = id,
      name = name,
      providerId = providerId,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
  }
}
