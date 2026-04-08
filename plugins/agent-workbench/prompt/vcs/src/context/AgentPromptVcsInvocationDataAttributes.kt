// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.DataContext

internal const val AGENT_PROMPT_VCS_INVOCATION_DATA_CONTEXT_KEY: String = "dataContext"

internal fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
  return attributes[AGENT_PROMPT_VCS_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}
