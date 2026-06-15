// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind

const val BUILT_IN_LAUNCH_PROFILE_PREFIX: String = "builtin:"

data class AgentSessionLaunchProfileSnapshot(
  @JvmField val builtInProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val userProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val activeProfileId: String?,
) {
  val allProfiles: List<AgentPromptLaunchProfile>
    get() = effectiveLaunchProfiles(builtInProfiles, userProfiles)
}

fun effectiveLaunchProfiles(
  builtInProfiles: List<AgentPromptLaunchProfile>,
  userProfiles: List<AgentPromptLaunchProfile>,
): List<AgentPromptLaunchProfile> {
  val userProfilesById = LinkedHashMap<String, AgentPromptLaunchProfile>()
  userProfiles.forEach { profile -> userProfilesById[profile.id] = profile }
  val builtInProfileIds = builtInProfiles.mapTo(HashSet()) { profile -> profile.id }
  return builtInProfiles.map { profile -> userProfilesById[profile.id] ?: profile } +
         userProfiles.filter { profile -> profile.id !in builtInProfileIds }
}

fun launchProfileEditablePayload(profile: AgentPromptLaunchProfile): AgentPromptLaunchProfile {
  return profile.copy(
    id = "",
    kind = AgentPromptLaunchProfileKind.USER,
  )
}

fun launchProfileMatchesBuiltIn(
  profile: AgentPromptLaunchProfile,
  builtInProfile: AgentPromptLaunchProfile,
): Boolean {
  return launchProfileEditablePayload(profile) == launchProfileEditablePayload(builtInProfile)
}

fun normalizedUserLaunchProfile(profile: AgentPromptLaunchProfile): AgentPromptLaunchProfile {
  return profile.copy(
    kind = AgentPromptLaunchProfileKind.USER,
  )
}

fun builtInLaunchProfileId(provider: AgentSessionProvider, launchMode: AgentSessionLaunchMode): String {
  return "$BUILT_IN_LAUNCH_PROFILE_PREFIX${provider.value}:${launchMode.name.lowercase()}"
}

fun buildBuiltInLaunchProfiles(
  menuModel: AgentSessionProviderMenuModel,
  resolveName: (AgentSessionProviderMenuItem) -> String,
): List<AgentPromptLaunchProfile> {
  return (menuModel.standardItems + menuModel.yoloItems)
    .filter(AgentSessionProviderMenuItem::isEnabled)
    .map { item ->
      AgentPromptLaunchProfile(
        id = builtInLaunchProfileId(item.bridge.provider, item.mode),
        name = resolveName(item),
        kind = AgentPromptLaunchProfileKind.BUILT_IN,
        providerId = item.bridge.provider.value,
        launchMode = item.mode,
      )
    }
}

fun generationSettingsForPlanMode(
  generationSettings: AgentPromptGenerationSettings,
  startInPlanMode: Boolean,
): AgentPromptGenerationSettings {
  if (!startInPlanMode) {
    return generationSettings.copy(planReasoningEffort = null)
  }
  return generationSettings
}

fun initialMessageRequestForLaunchProfile(@Suppress("UNUSED_PARAMETER") profile: AgentPromptLaunchProfile): AgentPromptInitialMessageRequest {
  return AgentPromptInitialMessageRequest(prompt = "")
}
