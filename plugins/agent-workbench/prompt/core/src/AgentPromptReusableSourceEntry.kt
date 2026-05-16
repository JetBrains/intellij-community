// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.util.NlsSafe

enum class AgentPromptReusableSourceKind {
  PROMPT_FILE,
  COMMAND,
  SKILL,
}

data class AgentPromptReusableSourceEntry(
  @JvmField val id: String,
  @JvmField val label: @NlsSafe String,
  @JvmField val insertText: @NlsSafe String,
  @JvmField val kind: AgentPromptReusableSourceKind,
  val provider: AgentSessionProvider? = null,
  @JvmField val description: @NlsSafe String? = null,
  @JvmField val sourcePath: String? = null,
)
