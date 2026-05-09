// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.DataContext

internal fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
  return attributes[AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}
