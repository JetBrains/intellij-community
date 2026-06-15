// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.providers.effectiveLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.launchProfileMatchesBuiltIn
import com.intellij.agent.workbench.sessions.core.providers.normalizedUserLaunchProfile

internal class AgentPromptLaunchProfileState(
  private val builtInProfiles: () -> List<AgentPromptLaunchProfile>,
  private val canApplyProfile: (AgentPromptLaunchProfile) -> Boolean,
) {
  private val userProfilesById = LinkedHashMap<String, AgentPromptLaunchProfile>()
  private var explicitDefaultProfileId: String? = null
  private var implicitDefaultProfileId: String? = null

  var selectedProfileId: String? = null
    private set

  val persistedDefaultProfileId: String?
    get() = explicitDefaultProfileId

  val effectiveDefaultProfileId: String?
    get() = explicitDefaultProfileId ?: implicitDefaultProfileId

  fun restore(
    preferences: AgentPromptLauncherBridge.ProviderPreferences,
    implicitDefaultProfileId: String?,
  ) {
    userProfilesById.clear()
    preferences.launchProfiles.forEach { profile -> userProfilesById[profile.id] = profile }
    explicitDefaultProfileId = preferences.activeLaunchProfileId
    this.implicitDefaultProfileId = implicitDefaultProfileId
    selectedProfileId = effectiveDefaultProfileId
  }

  fun userProfiles(): List<AgentPromptLaunchProfile> = userProfilesById.values.toList()

  fun allManagedProfiles(): List<AgentPromptLaunchProfile> {
    return effectiveLaunchProfiles(builtInProfiles(), userProfiles())
  }

  fun launchableProfiles(): List<AgentPromptLaunchProfile> {
    return allManagedProfiles().filter(canApplyProfile)
  }

  fun findProfile(profileId: String?): AgentPromptLaunchProfile? {
    if (profileId == null) return null
    return allManagedProfiles().firstOrNull { profile -> profile.id == profileId }
  }

  fun selectedProfile(): AgentPromptLaunchProfile? = findProfile(selectedProfileId)

  fun selectProfile(profile: AgentPromptLaunchProfile) {
    selectedProfileId = profile.id
  }

  fun clearSelectedProfile() {
    selectedProfileId = null
  }

  fun selectedProfileIdForPresentation(draft: AgentPromptLaunchProfile?): String? {
    return profileForPresentation(draft)?.id
  }

  fun profileForPresentation(draft: AgentPromptLaunchProfile?): AgentPromptLaunchProfile? {
    val selectedProfile = selectedProfile()
    if (selectedProfile != null && !isSelectedProfileModified(draft)) {
      return selectedProfile
    }
    return matchingLaunchableProfile(draft)
  }

  fun isSelectedProfileModified(draft: AgentPromptLaunchProfile?): Boolean {
    val selectedProfile = selectedProfile() ?: return false
    draft ?: return false
    return selectedProfile.profilePayload() != draft.profilePayload()
  }

  fun defaultAction(draft: AgentPromptLaunchProfile?): AgentPromptDefaultProfileAction? {
    draft ?: return null
    if (!canApplyProfile(draft)) {
      return null
    }
    val matchingProfile = matchingLaunchableProfile(draft)
    if (matchingProfile != null) {
      return if (matchingProfile.id == effectiveDefaultProfileId) null else AgentPromptDefaultProfileAction.MakeDefault(matchingProfile)
    }
    return AgentPromptDefaultProfileAction.SaveAsDefault
  }

  private fun matchingLaunchableProfile(draft: AgentPromptLaunchProfile?): AgentPromptLaunchProfile? {
    draft ?: return null
    return launchableProfiles().firstOrNull { profile -> profile.profilePayload() == draft.profilePayload() }
  }

  fun saveNewProfile(profile: AgentPromptLaunchProfile) {
    val normalizedProfile = normalizedUserLaunchProfile(profile)
    userProfilesById[normalizedProfile.id] = normalizedProfile
  }

  fun saveProfile(profile: AgentPromptLaunchProfile): Boolean {
    val builtInProfile = builtInProfiles().firstOrNull { item -> item.id == profile.id }
    when {
      builtInProfile != null && launchProfileMatchesBuiltIn(profile, builtInProfile) -> {
        userProfilesById.remove(profile.id)
      }
      builtInProfile != null -> {
        userProfilesById[profile.id] = normalizedUserLaunchProfile(profile)
      }
      profile.kind == AgentPromptLaunchProfileKind.USER && profile.id in userProfilesById -> {
        userProfilesById[profile.id] = normalizedUserLaunchProfile(profile)
      }
      else -> return false
    }
    return true
  }

  fun setDefaultProfile(profile: AgentPromptLaunchProfile) {
    explicitDefaultProfileId = profile.id
  }

  fun saveDraftAsDefault(draft: AgentPromptLaunchProfile): AgentPromptLaunchProfile {
    val profile = normalizedUserLaunchProfile(draft)
    userProfilesById[profile.id] = profile
    selectedProfileId = profile.id
    explicitDefaultProfileId = profile.id
    return profile
  }

  fun canDeleteProfile(profile: AgentPromptLaunchProfile): Boolean {
    return profile.id in userProfilesById
  }

  fun deleteProfile(profile: AgentPromptLaunchProfile): Boolean {
    if (profile.id !in userProfilesById) {
      return false
    }
    val resetsBuiltInProfile = builtInProfiles().any { item -> item.id == profile.id }
    userProfilesById.remove(profile.id)
    if (!resetsBuiltInProfile && selectedProfileId == profile.id) {
      selectedProfileId = null
    }
    if (!resetsBuiltInProfile && explicitDefaultProfileId == profile.id) {
      explicitDefaultProfileId = null
    }
    return true
  }
}

internal sealed interface AgentPromptDefaultProfileAction {
  data class MakeDefault(val profile: AgentPromptLaunchProfile) : AgentPromptDefaultProfileAction
  data object SaveAsDefault : AgentPromptDefaultProfileAction
}

internal fun AgentPromptLaunchProfile.profilePayload(): AgentPromptLaunchProfile {
  return copy(
    id = "",
    name = "",
    kind = AgentPromptLaunchProfileKind.USER,
  )
}
