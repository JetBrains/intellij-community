// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem

internal data class AgentPromptAddContextRequest(
    @JvmField val contextItems: List<AgentPromptContextItem>,
    @JvmField val target: AgentPromptAddContextTargetCandidate?,
)

internal enum class AgentPromptAddContextApplyResult {
    ADDED,
    ALREADY_ADDED,
}

internal const val ADD_TO_AGENT_CONTEXT_SOURCE_ID: String = "agentWorkbench.addToAgentContext"
