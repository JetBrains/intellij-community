// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.frame.AgentThreadViewOpenModeSettings
import com.intellij.agent.workbench.settings.AgentWorkbenchSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AgentThreadsProjectScopeSettings {
  fun isCurrentProjectOnly(): Boolean {
    return isCurrentProjectOnly(openInDedicatedFrame = AgentThreadViewOpenModeSettings.openInDedicatedFrame())
  }

  fun isCurrentProjectOnly(openInDedicatedFrame: Boolean): Boolean {
    return AgentWorkbenchSettings.getInstance().agentThreadsCurrentProjectOnlyOverride ?: defaultCurrentProjectOnly(openInDedicatedFrame)
  }

  fun setCurrentProjectOnly(enabled: Boolean) {
    val defaultValue = defaultCurrentProjectOnly(openInDedicatedFrame = AgentThreadViewOpenModeSettings.openInDedicatedFrame())
    val override = enabled.takeIf { it != defaultValue }
    AgentWorkbenchSettings.getInstance().setAgentThreadsCurrentProjectOnlyOverride(override)
  }

  fun defaultCurrentProjectOnly(openInDedicatedFrame: Boolean): Boolean {
    return !openInDedicatedFrame
  }
}
