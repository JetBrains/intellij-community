// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.core.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName

interface AgentSessionProviderFeatureSettings {
  fun isProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String): Boolean = true

  fun setProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String, enabled: Boolean) {
  }
}

object AgentSessionProviderFeatureSettingsExtensions {
  val EP_NAME: ExtensionPointName<AgentSessionProviderFeatureSettings> =
    ExtensionPointName("com.intellij.agent.workbench.sessionProviderFeatureSettings")

  fun isProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String): Boolean {
    return EP_NAME.extensionList.firstOrNull()?.isProviderFeatureEnabled(provider, featureId) ?: true
  }

  fun setProviderFeatureEnabled(provider: AgentSessionProvider, featureId: String, enabled: Boolean) {
    EP_NAME.extensionList.firstOrNull()?.setProviderFeatureEnabled(provider, featureId, enabled)
  }
}
