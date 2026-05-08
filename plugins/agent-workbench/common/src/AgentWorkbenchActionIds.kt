// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

object AgentWorkbenchActionIds {
  object Sessions {
    const val OPEN_DEDICATED_FRAME: String = "AgentWorkbenchSessions.OpenDedicatedFrame"
    const val TOOL_WINDOW_GEAR_ACTIONS: String = "AgentWorkbenchSessions.ToolWindow.GearActions"
    const val BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.BindPendingAgentThreadFromEditorTab"

    object MainToolbar {
      const val NEW_THREAD: String = "AgentWorkbenchSessions.MainToolbar.NewThread"
    }

    object TreePopup {
      const val GROUP: String = "AgentWorkbenchSessions.TreePopup"
      const val NEW_THREAD: String = "AgentWorkbenchSessions.TreePopup.NewThread"
      const val ARCHIVE: String = "AgentWorkbenchSessions.TreePopup.Archive"
    }

    object EditorTab {
      const val NEW_THREAD_QUICK: String = "AgentWorkbenchSessions.EditorTab.NewThreadQuick"
      const val NEW_THREAD_POPUP: String = "AgentWorkbenchSessions.EditorTab.NewThreadPopup"
      const val PREVIOUS_PROPOSED_PLAN: String = "AgentWorkbenchSessions.EditorTab.PreviousProposedPlan"
      const val NEXT_PROPOSED_PLAN: String = "AgentWorkbenchSessions.EditorTab.NextProposedPlan"
    }
  }
}
