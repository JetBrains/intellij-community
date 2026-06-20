package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.core.session.AgentSessionProvider
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
  fun getAndSetProviderPreferencesRoundTrip() {
    val service = uiPreferencesService()

    val prefs = AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
      containerModeEnabled = true,
      launchProfiles = listOf(launchProfile("user:careful", "Careful", AgentSessionProvider.CLAUDE.value)),
      defaultLaunchProfileId = "user:careful",
    )
    service.setProviderPreferences(prefs)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
    assertThat(loaded.containerModeEnabled).isTrue()
    assertThat(loaded.launchProfiles.map(AgentPromptLaunchProfile::id)).containsExactly("user:careful")
    assertThat(loaded.defaultLaunchProfileId).isEqualTo("user:careful")
  }

  @Test
  fun updateProviderOptionsOnLaunchPersistsProviderOptions() {
    val service = uiPreferencesService()
    val request = AgentPromptInitialMessageRequest(
      prompt = "fix bug",
      providerOptionIds = setOf("plan_mode"),
    )

    service.updateProviderOptionsOnLaunch(AgentSessionProvider.CODEX.value, request)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("codex" to setOf("plan_mode")))
  }

  @Test
  fun updateProviderOptionsOnLaunchMergesProviderOptions() {
    val service = uiPreferencesService()
    service.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
      launchProfiles = listOf(launchProfile("user:claude", "Claude Custom", AgentSessionProvider.CLAUDE.value)),
      defaultLaunchProfileId = "user:claude",
    ))

    service.updateProviderOptionsOnLaunch(
      AgentSessionProvider.CODEX.value,
      AgentPromptInitialMessageRequest(prompt = "test", providerOptionIds = setOf("fast")),
    )

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).containsEntry("claude", setOf("plan_mode"))
    assertThat(loaded.providerOptionsByProviderId).containsEntry("codex", setOf("fast"))
    assertThat(loaded.launchProfiles.map(AgentPromptLaunchProfile::id)).containsExactly("user:claude")
    assertThat(loaded.defaultLaunchProfileId).isEqualTo("user:claude")
  }

  @Test
  fun loadStateKeepsProviderOptionsSeparateFromLaunchProfiles() {
    val service = uiPreferencesService()

    service.loadState(AgentSessionUiPreferencesStateService.UiPreferencesState(
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
    assertThat(loaded.defaultLaunchProfileId).isNull()
  }

  @Test
  fun updateProviderOptionsOnLaunchWithNullRequestPreservesExistingOptions() {
    val service = uiPreferencesService()
    service.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = mapOf("claude" to setOf("plan_mode")),
    ))

    service.updateProviderOptionsOnLaunch(AgentSessionProvider.CODEX.value, null)

    val loaded = service.getProviderPreferences()
    assertThat(loaded.providerOptionsByProviderId).isEqualTo(mapOf("claude" to setOf("plan_mode")))
  }

  @Test
  fun launchProfileStateServiceStoresSeparateVcsMergeActiveProfile() {
    val service = AgentSessionLaunchProfileStateService()

    service.setLaunchProfiles(
      profiles = listOf(launchProfile("user:general", "General", AgentSessionProvider.CLAUDE.value)),
      defaultProfileId = "user:general",
    )
    service.setActiveVcsMergeLaunchProfileId("builtin:codex:standard")

    assertThat(service.getDefaultLaunchProfileId()).isEqualTo("user:general")
    assertThat(service.getActiveVcsMergeLaunchProfileId()).isEqualTo("builtin:codex:standard")
  }

  @Test
  fun launchProfilesAreStoredInDedicatedStateService() {
    val launchProfileStateService = AgentSessionLaunchProfileStateService()
    val uiPreferencesService = AgentSessionUiPreferencesStateService(launchProfileStateService)

    uiPreferencesService.setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      launchProfiles = listOf(launchProfile("user:fast", "Fast", AgentSessionProvider.CODEX.value)),
      defaultLaunchProfileId = "user:fast",
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
      defaultProfileId = "user:careful",
    )

    assertThat(service.getUserLaunchProfiles().map(AgentPromptLaunchProfile::id))
      .containsExactly("user:careful")
    assertThat(service.getDefaultLaunchProfileId()).isEqualTo("user:careful")
  }

  @Test
  fun launchProfileStateServiceStoresBuiltInOverrides() {
    val service = AgentSessionLaunchProfileStateService()
    val profile = launchProfile("builtin:codex:standard", "Careful Codex", AgentSessionProvider.CODEX.value)

    service.setLaunchProfiles(
      profiles = listOf(profile),
      defaultProfileId = profile.id,
    )

    assertThat(service.getUserLaunchProfiles()).containsExactly(profile)
    assertThat(service.getDefaultLaunchProfileId()).isEqualTo(profile.id)
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
