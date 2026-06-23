// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionLaunchProfileResolver
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionResolvedLaunchProfile
import com.intellij.platform.ai.agent.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.platform.ai.agent.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.platform.ai.agent.sessions.core.providers.resolveAgentSessionLaunchProfile
import com.intellij.openapi.components.service

internal class AgentSessionLaunchProfileResolverImpl(
  private val uiPreferencesState: AgentSessionUiPreferencesStateService = service(),
) : AgentSessionLaunchProfileResolver {
  override fun resolveLaunchProfile(
    launchProfileId: String?,
    requiredProvider: AgentSessionProvider?,
  ): AgentSessionResolvedLaunchProfile? {
    val providers = AgentSessionProviders.allProviders()
    val providerMenuModel = buildAgentSessionProviderMenuModel(
      bridges = providers,
      availabilityByProvider = providers.associate { descriptor -> descriptor.provider to true },
    )
    return resolveAgentSessionLaunchProfile(
      launchProfileId = launchProfileId,
      requiredProvider = requiredProvider,
      defaultProfileId = uiPreferencesState.getDefaultLaunchProfileId(),
      builtInProfiles = buildBuiltInLaunchProfiles(providerMenuModel) { item -> item.bridge.displayNameFallback },
      userProfiles = uiPreferencesState.getUserLaunchProfiles(),
      providerDescriptors = providers,
    )
  }
}
