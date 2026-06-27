// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import org.jetbrains.annotations.ApiStatus

const val BUILT_IN_LAUNCH_PROFILE_PREFIX: String = "builtin:"

data class AgentSessionLaunchProfileSnapshot(
  @JvmField val builtInProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val userProfiles: List<AgentPromptLaunchProfile>,
) {
  val allProfiles: List<AgentPromptLaunchProfile>
    get() = effectiveLaunchProfiles(builtInProfiles, userProfiles)
}

@ApiStatus.Internal
data class AgentSessionResolvedLaunchProfile(
  @JvmField val id: String,
  @JvmField val profile: AgentPromptLaunchProfile,
  val provider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
)

@ApiStatus.Internal
interface AgentSessionLaunchProfileResolver {
  fun resolveLaunchProfile(
    launchProfileId: String?,
    requiredProvider: AgentSessionProvider? = null,
  ): AgentSessionResolvedLaunchProfile?
}

@ApiStatus.Internal
fun resolveAgentSessionLaunchProfile(
  launchProfileId: String?,
  requiredProvider: AgentSessionProvider? = null,
  defaultProfileId: String? = null,
  builtInProfiles: List<AgentPromptLaunchProfile>,
  userProfiles: List<AgentPromptLaunchProfile>,
  providerDescriptors: Iterable<AgentSessionProviderDescriptor>,
): AgentSessionResolvedLaunchProfile? {
  val descriptorsByProvider = providerDescriptors.associateBy { descriptor -> descriptor.provider.value }
  val effectiveProfiles = effectiveLaunchProfiles(builtInProfiles, userProfiles)

  fun resolve(profile: AgentPromptLaunchProfile): AgentSessionResolvedLaunchProfile? {
    val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return null
    if (requiredProvider != null && provider != requiredProvider) return null
    val descriptor = descriptorsByProvider[provider.value] ?: return null
    if (profile.launchMode !in descriptor.supportedLaunchModes) return null
    return AgentSessionResolvedLaunchProfile(
      id = profile.id,
      profile = profile,
      provider = provider,
      launchMode = profile.launchMode,
      generationSettings = descriptor.sanitizeGenerationSettings(profile.generationSettings),
    )
  }

  val normalizedProfileId = launchProfileId?.trim()?.takeIf(String::isNotEmpty)
  if (normalizedProfileId != null) {
    effectiveProfiles.firstOrNull { profile -> profile.id == normalizedProfileId }?.let { profile ->
      resolve(profile)?.let { resolved -> return resolved }
    }
  }

  val normalizedDefaultProfileId = defaultProfileId?.trim()?.takeIf(String::isNotEmpty)
  if (normalizedDefaultProfileId != null && normalizedDefaultProfileId != normalizedProfileId) {
    effectiveProfiles.firstOrNull { profile -> profile.id == normalizedDefaultProfileId }?.let { profile ->
      resolve(profile)?.let { resolved -> return resolved }
    }
  }

  val compatibleProfiles = effectiveProfiles.mapNotNull(::resolve)
  if (requiredProvider != null) {
    compatibleProfiles.firstOrNull { profile ->
      profile.id == builtInLaunchProfileId(requiredProvider, AgentSessionLaunchMode.STANDARD)
    }?.let { profile -> return profile }
  }
  return compatibleProfiles.firstOrNull { profile -> profile.launchMode == AgentSessionLaunchMode.STANDARD }
         ?: compatibleProfiles.firstOrNull()
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

@ApiStatus.Internal
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
