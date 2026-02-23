// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionProviderIconIds
import org.jetbrains.jewel.ui.icon.PathIconKey

internal object AgentSessionsIconKeys {
  val Claude: PathIconKey = PathIconKey("icons/claude@14x14.svg", AgentWorkbenchCommonIcons::class.java)
  val Codex: PathIconKey = PathIconKey("icons/codex@14x14.svg", AgentWorkbenchCommonIcons::class.java)

  fun byId(iconId: String): PathIconKey? {
    return when (iconId) {
      AgentSessionProviderIconIds.CLAUDE -> Claude
      AgentSessionProviderIconIds.CODEX -> Codex
      else -> null
    }
  }
}
