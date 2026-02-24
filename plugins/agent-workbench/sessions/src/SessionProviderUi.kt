// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.openapi.util.IconLoader
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal fun providerDisplayName(provider: AgentSessionProvider): @Nls String {
  val bridge = AgentSessionProviderBridges.find(provider)
  return if (bridge != null) AgentSessionsBundle.message(bridge.displayNameKey) else provider.value
}

internal fun providerIcon(provider: AgentSessionProvider): Icon? {
  val bridge = AgentSessionProviderBridges.find(provider) ?: return null
  val icon = bridge.icon
  if (icon.path.isBlank()) return null
  return runCatching {
    IconLoader.getIcon(icon.path, icon.iconClass)
  }.getOrNull()
}
