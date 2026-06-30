// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

// @spec community/plugins/agent-workbench/spec/frame/agent-dedicated-frame.spec.md

import com.intellij.agent.workbench.settings.AgentWorkbenchSettings

object AgentThreadViewOpenModeSettings {
  fun openInDedicatedFrame(): Boolean = AgentWorkbenchSettings.getInstance().openInDedicatedFrame

  fun setOpenInDedicatedFrame(enabled: Boolean) {
    AgentWorkbenchSettings.getInstance().setOpenInDedicatedFrame(enabled)
  }
}
