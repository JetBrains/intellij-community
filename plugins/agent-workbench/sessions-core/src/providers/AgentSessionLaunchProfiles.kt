// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptPlanEffortMode
import com.intellij.agent.workbench.prompt.core.AgentPromptPlanEffortModeKind
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort

const val BUILT_IN_LAUNCH_PROFILE_PREFIX: String = "builtin:"

data class AgentSessionLaunchProfileSnapshot(
  @JvmField val builtInProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val userProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val activeProfileId: String?,
) {
  val allProfiles: List<AgentPromptLaunchProfile>
    get() = builtInProfiles + userProfiles
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

fun generationSettingsForPlanEffort(
  generationSettings: AgentPromptGenerationSettings,
  planEffort: AgentPromptPlanEffortMode,
  startInPlanMode: Boolean,
): AgentPromptGenerationSettings {
  if (!startInPlanMode) {
    return generationSettings.copy(planReasoningEffort = null)
  }
  val planReasoningEffort = when (planEffort.kind) {
    AgentPromptPlanEffortModeKind.SAME_AS_NORMAL -> null
    AgentPromptPlanEffortModeKind.PROVIDER_DEFAULT -> AgentPromptReasoningEffort.AUTO
    AgentPromptPlanEffortModeKind.EXPLICIT -> planEffort.explicitEffort ?: AgentPromptReasoningEffort.AUTO
  }
  return generationSettings.copy(planReasoningEffort = planReasoningEffort)
}

fun initialMessageRequestForLaunchProfile(profile: AgentPromptLaunchProfile): AgentPromptInitialMessageRequest {
  val providerOptionIds = if (profile.startInPlanMode) setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE) else emptySet()
  return AgentPromptInitialMessageRequest(prompt = "", providerOptionIds = providerOptionIds)
}
