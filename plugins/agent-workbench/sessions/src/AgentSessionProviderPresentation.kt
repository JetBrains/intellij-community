// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

internal fun providerDisplayName(provider: AgentSessionProvider): @NlsSafe String {
  val bridge = AgentSessionProviders.find(provider)
  if (bridge == null) {
    return provider.value
  }
  return runCatching { AgentSessionsBundle.message(bridge.displayNameKey) }
    .getOrDefault(bridge.displayNameFallback)
}

internal fun providerIcon(provider: AgentSessionProvider): Icon? {
  return AgentSessionProviders.find(provider)?.icon
}
