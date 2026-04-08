// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider

internal class AgentMergeResolveActionProvider : MergeResolveActionProvider {
  override val action: AnAction
    get() = requireNotNull(ActionManager.getInstance().getAction(RESOLVE_WITH_AGENT_ACTION_ID)) {
      "Action $RESOLVE_WITH_AGENT_ACTION_ID is not registered"
    }

  private companion object {
    const val RESOLVE_WITH_AGENT_ACTION_ID = "Merge.AgentResolveConflicts"
  }
}
