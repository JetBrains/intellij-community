// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import org.jetbrains.jewel.ui.icon.PathIconKey

internal object AgentSessionsIconKeys {
  val Claude: PathIconKey = PathIconKey("icons/claude@14x14.svg", AgentSessionsIconKeys::class.java)
  val Codex: PathIconKey = PathIconKey("icons/codex@14x14.svg", AgentSessionsIconKeys::class.java)
}
