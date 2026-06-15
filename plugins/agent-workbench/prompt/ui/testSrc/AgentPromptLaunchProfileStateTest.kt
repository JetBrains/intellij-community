// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.builtInLaunchProfileId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AgentPromptLaunchProfileStateTest {
  private val standardBuiltInProfile = AgentPromptLaunchProfile(
    id = builtInLaunchProfileId(AgentSessionProvider.CODEX, AgentSessionLaunchMode.STANDARD),
    name = "Codex",
    kind = AgentPromptLaunchProfileKind.BUILT_IN,
    providerId = AgentSessionProvider.CODEX.value,
  )
  private val yoloBuiltInProfile = AgentPromptLaunchProfile(
    id = builtInLaunchProfileId(AgentSessionProvider.CODEX, AgentSessionLaunchMode.YOLO),
    name = "Codex Full Auto",
    kind = AgentPromptLaunchProfileKind.BUILT_IN,
    providerId = AgentSessionProvider.CODEX.value,
    launchMode = AgentSessionLaunchMode.YOLO,
  )

  @Test
  fun implicitBuiltInDefaultProducesNoDefaultAction() {
    val state = createState()
    state.restore(AgentPromptLauncherBridge.ProviderPreferences(), implicitDefaultProfileId = standardBuiltInProfile.id)

    val action = state.defaultAction(standardBuiltInProfile.asDraft())

    assertThat(action).isNull()
  }

  @Test
  fun differentBuiltInProfileProducesMakeDefaultAction() {
    val state = createState()
    state.restore(AgentPromptLauncherBridge.ProviderPreferences(), implicitDefaultProfileId = standardBuiltInProfile.id)

    val action = state.defaultAction(yoloBuiltInProfile.asDraft())

    assertThat(action).isEqualTo(AgentPromptDefaultProfileAction.MakeDefault(yoloBuiltInProfile))
  }

  @Test
  fun nonDefaultUserProfileProducesMakeDefaultAction() {
    val carefulProfile = AgentPromptLaunchProfile(
      id = "user:careful",
      name = "Careful",
      providerId = AgentSessionProvider.CODEX.value,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
    val state = createState()
    state.restore(
      preferences = AgentPromptLauncherBridge.ProviderPreferences(launchProfiles = listOf(carefulProfile)),
      implicitDefaultProfileId = standardBuiltInProfile.id,
    )

    val action = state.defaultAction(carefulProfile.asDraft())

    assertThat(action).isEqualTo(AgentPromptDefaultProfileAction.MakeDefault(carefulProfile))
  }

  @Test
  fun modifiedControlsProduceSaveAsDefaultAction() {
    val state = createState()
    state.restore(AgentPromptLauncherBridge.ProviderPreferences(), implicitDefaultProfileId = standardBuiltInProfile.id)
    val draft = standardBuiltInProfile.asDraft().copy(
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )

    val action = state.defaultAction(draft)

    assertThat(action).isEqualTo(AgentPromptDefaultProfileAction.SaveAsDefault)
  }

  @Test
  fun matchingProfileDraftIsUsedForPresentationWhenSelectedProfileDiffers() {
    val carefulProfile = AgentPromptLaunchProfile(
      id = "user:careful",
      name = "Careful",
      providerId = AgentSessionProvider.CODEX.value,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
    val fastProfile = AgentPromptLaunchProfile(
      id = "user:fast",
      name = "Fast",
      providerId = AgentSessionProvider.CODEX.value,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.LOW),
    )
    val state = createState()
    state.restore(
      preferences = AgentPromptLauncherBridge.ProviderPreferences(
        launchProfiles = listOf(carefulProfile, fastProfile),
        activeLaunchProfileId = carefulProfile.id,
      ),
      implicitDefaultProfileId = standardBuiltInProfile.id,
    )

    val profile = state.profileForPresentation(fastProfile.asDraft())

    assertThat(profile).isEqualTo(fastProfile)
    assertThat(state.selectedProfileIdForPresentation(fastProfile.asDraft())).isEqualTo(fastProfile.id)
  }

  @Test
  fun unmatchedModifiedDraftHasNoProfileForPresentation() {
    val state = createState()
    state.restore(AgentPromptLauncherBridge.ProviderPreferences(), implicitDefaultProfileId = standardBuiltInProfile.id)
    val draft = standardBuiltInProfile.asDraft().copy(
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )

    val profile = state.profileForPresentation(draft)

    assertThat(profile).isNull()
    assertThat(state.selectedProfileIdForPresentation(draft)).isNull()
  }

  @Test
  fun profilePayloadMatchingIgnoresIdNameAndKind() {
    val draft = standardBuiltInProfile.copy(
      id = "draft",
      name = "Draft",
      kind = AgentPromptLaunchProfileKind.USER,
    )

    assertThat(draft.profilePayload()).isEqualTo(standardBuiltInProfile.profilePayload())
  }

  private fun createState(): AgentPromptLaunchProfileState {
    return AgentPromptLaunchProfileState(
      builtInProfiles = { listOf(standardBuiltInProfile, yoloBuiltInProfile) },
      canApplyProfile = { true },
    )
  }

  private fun AgentPromptLaunchProfile.asDraft(): AgentPromptLaunchProfile {
    return copy(
      id = "",
      name = "",
      kind = AgentPromptLaunchProfileKind.USER,
    )
  }
}
