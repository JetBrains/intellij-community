// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

object AgentWorkbenchActionIds {
  object Prompt {
    const val MANAGE_LAUNCH_PROFILES: String = "AgentWorkbenchPrompt.ManageLaunchProfiles"
  }

  object Sessions {
    const val OPEN_DEDICATED_FRAME: String = "AgentWorkbenchSessions.OpenDedicatedFrame"
    const val SWITCH_SOURCE_AND_CHAT: String = "AgentWorkbenchSessions.SwitchSourceAndChat"
    const val TOOL_WINDOW_GEAR_ACTIONS: String = "AgentWorkbenchSessions.ToolWindow.GearActions"
    const val BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.BindPendingAgentThreadFromEditorTab"

    object MainToolbar {
      const val NEW_THREAD: String = "AgentWorkbenchSessions.MainToolbar.NewThread"
      const val ACTIVITY: String = "AgentWorkbenchSessions.MainToolbar.Activity"
    }

    object TreePopup {
      const val GROUP: String = "AgentWorkbenchSessions.TreePopup"
      const val NEW_THREAD: String = "AgentWorkbenchSessions.TreePopup.NewThread"
      const val ARCHIVE: String = "AgentWorkbenchSessions.TreePopup.Archive"
    }

    object EditorTab {
      const val NEW_THREAD: String = "AgentWorkbenchSessions.EditorTab.NewThread"
      const val PREVIOUS_PROPOSED_PLAN: String = "AgentWorkbenchSessions.EditorTab.PreviousProposedPlan"
      const val NEXT_PROPOSED_PLAN: String = "AgentWorkbenchSessions.EditorTab.NextProposedPlan"
    }
  }
}
