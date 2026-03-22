// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData

internal class AgentPromptSelectedEditorFallbackContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.FALLBACK

  override val order: Int
    get() = 0

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val snapshot = AgentPromptEditorContextSupport.buildSnapshotFromSelectedEditor(invocationData.project) ?: return emptyList()
    return AgentPromptEditorContextSupport.buildContextItems(snapshot)
  }
}
