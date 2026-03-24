// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal fun providerDisplayName(provider: AgentSessionProvider): @Nls String {
  val bridge = AgentSessionProviders.find(provider)
  return if (bridge != null) AgentSessionsBundle.message(bridge.displayNameKey) else provider.value
}

internal fun providerIcon(provider: AgentSessionProvider): Icon? {
  val bridge = AgentSessionProviders.find(provider) ?: return null
  return bridge.icon
}
