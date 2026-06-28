// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.Icon

const val BUILT_IN_LAUNCH_PROFILE_PREFIX: String = "builtin:"
private const val ACP_PROVIDER_ID = "acp"

@ApiStatus.Internal
data class AgentSessionBuiltInLaunchProfileContribution(
  @JvmField val id: String,
  @JvmField val name: String,
  val provider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  @JvmField val launchTargetId: String? = null,
  @JvmField val icon: Icon? = null,
)

@ApiStatus.Internal
interface AgentSessionLaunchProfileContributor {
  fun buildBuiltInLaunchProfiles(project: Project?): List<AgentSessionBuiltInLaunchProfileContribution>
}

@ApiStatus.Internal
object AgentSessionLaunchProfileContributors {
  private val EP = ExtensionPointName<AgentSessionLaunchProfileContributor>("com.intellij.agent.workbench.sessionLaunchProfileContributor")

  fun buildBuiltInLaunchProfiles(project: Project?): List<AgentSessionBuiltInLaunchProfileContribution> {
    return if (EP.hasAnyExtensions()) EP.extensionList.flatMap { contributor -> contributor.buildBuiltInLaunchProfiles(project) } else emptyList()
  }
}

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
  @JvmField val launchTargetId: String?,
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
    val normalizedProfile = normalizeLaunchProfileForResolution(profile)
    val provider = AgentSessionProvider.fromOrNull(normalizedProfile.providerId) ?: return null
    if (requiredProvider != null && provider != requiredProvider) return null
    val descriptor = descriptorsByProvider[provider.value] ?: return null
    if (normalizedProfile.launchMode !in descriptor.supportedLaunchModes) return null
    return AgentSessionResolvedLaunchProfile(
      id = normalizedProfile.id,
      profile = normalizedProfile,
      provider = provider,
      launchMode = normalizedProfile.launchMode,
      launchTargetId = normalizedProfile.launchTargetId,
      generationSettings = descriptor.sanitizeGenerationSettings(normalizedProfile.generationSettings),
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
fun builtInLaunchTargetProfileId(
  provider: AgentSessionProvider,
  launchTargetId: String,
  launchMode: AgentSessionLaunchMode,
): String {
  val encodedTargetId = Base64.getUrlEncoder().withoutPadding().encodeToString(launchTargetId.toByteArray(StandardCharsets.UTF_8))
  return "$BUILT_IN_LAUNCH_PROFILE_PREFIX${provider.value}:target:$encodedTargetId:${launchMode.name.lowercase()}"
}

@ApiStatus.Internal
fun buildBuiltInLaunchProfiles(
  menuModel: AgentSessionProviderMenuModel,
  resolveName: (AgentSessionProviderMenuItem) -> String,
  project: Project? = null,
): List<AgentPromptLaunchProfile> {
  val providerProfiles = (menuModel.standardItems + menuModel.yoloItems)
    .filter(AgentSessionProviderMenuItem::isEnabled)
    .filter { item -> item.bridge.supportsDefaultLaunchProfile }
    .map { item ->
      AgentPromptLaunchProfile(
        id = builtInLaunchProfileId(item.bridge.provider, item.mode),
        name = resolveName(item),
        kind = AgentPromptLaunchProfileKind.BUILT_IN,
        providerId = item.bridge.provider.value,
        launchMode = item.mode,
      )
    }
  val contributedProfiles = AgentSessionLaunchProfileContributors.buildBuiltInLaunchProfiles(project).map { profile ->
    AgentPromptLaunchProfile(
      id = profile.id,
      name = profile.name,
      kind = AgentPromptLaunchProfileKind.BUILT_IN,
      providerId = profile.provider.value,
      launchMode = profile.launchMode,
      launchTargetId = profile.launchTargetId,
    )
  }
  return (providerProfiles + contributedProfiles).distinctBy(AgentPromptLaunchProfile::id)
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

private fun normalizeLaunchProfileForResolution(profile: AgentPromptLaunchProfile): AgentPromptLaunchProfile {
  val launchTargetId = profile.launchTargetId?.trim()?.takeIf(String::isNotEmpty)
  if (launchTargetId != null) {
    return if (launchTargetId == profile.launchTargetId) profile else profile.copy(launchTargetId = launchTargetId)
  }

  if (profile.providerId == ACP_PROVIDER_ID) {
    val legacyAgentKey = profile.generationSettings.modelId?.trim()?.takeIf(String::isNotEmpty)
    if (legacyAgentKey != null) {
      return profile.copy(
        launchTargetId = legacyAgentKey,
        generationSettings = profile.generationSettings.copy(modelId = null),
      )
    }
  }

  return if (profile.launchTargetId == null) profile else profile.copy(launchTargetId = null)
}
