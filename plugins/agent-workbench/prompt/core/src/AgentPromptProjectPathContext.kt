// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey

const val AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_ID: String = "agent.workbench.prompt.projectPathContext"

data class AgentPromptProjectPathContext(
  @JvmField val path: String,
  @JvmField val displayName: String? = null,
)

@JvmField
val AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY: DataKey<AgentPromptProjectPathContext> =
  DataKey.create(AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_ID)

fun getAgentPromptProjectPathContext(dataContext: DataContext?): AgentPromptProjectPathContext? {
  return dataContext?.getData(AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY)
}
