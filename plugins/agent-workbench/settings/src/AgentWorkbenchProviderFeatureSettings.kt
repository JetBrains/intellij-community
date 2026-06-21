// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.settings

import com.intellij.agent.workbench.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderFeatureSettings
import com.intellij.openapi.components.service

internal class AgentWorkbenchProviderFeatureSettings : AgentSessionProviderFeatureSettings {
  override fun isProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String): Boolean {
    return service<AgentSessionProviderSettingsService>().isProviderFeatureEnabled(provider, featureId)
  }

  override fun setProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String, enabled: Boolean) {
    service<AgentSessionProviderSettingsService>().setProviderFeatureEnabled(provider, featureId, enabled)
  }
}
