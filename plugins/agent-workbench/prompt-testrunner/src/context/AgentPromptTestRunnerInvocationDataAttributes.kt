// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.DataContext

internal const val AGENT_PROMPT_TEST_RUNNER_INVOCATION_DATA_CONTEXT_KEY: String = "dataContext"

internal fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
  return attributes[AGENT_PROMPT_TEST_RUNNER_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}

