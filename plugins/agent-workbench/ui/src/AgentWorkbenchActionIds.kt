// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AgentWorkbenchActionIds {
  object Prompt {
    const val MANAGE_LAUNCH_PROFILES: String = "AgentWorkbenchPrompt.ManageLaunchProfiles"
  }

  object Sessions {
    const val OPEN_DEDICATED_FRAME: String = "AgentWorkbenchSessions.OpenDedicatedFrame"
    const val TOGGLE_CURRENT_PROJECT_ONLY: String = "AgentWorkbenchSessions.ToggleCurrentProjectOnly"
    const val SWITCH_SOURCE_AND_THREAD_VIEW: String = "AgentWorkbenchSessions.SwitchSourceAndAgentThreadView"
    const val TOOL_WINDOW_GEAR_ACTIONS: String = "AgentWorkbenchSessions.ToolWindow.GearActions"
    const val TOOL_WINDOW_TITLE_ACTIONS: String = "AgentWorkbenchSessions.ToolWindow.TitleActions"
    const val TOOL_WINDOW_CREATE_TASK_FOLDER: String = "AgentWorkbenchSessions.ToolWindow.CreateTaskFolder"
    const val BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.BindPendingAgentThreadFromEditorTab"

    object MainToolbar {
      const val NEW_THREAD: String = "AgentWorkbenchSessions.MainToolbar.NewThread"
      const val ACTIVITY: String = "AgentWorkbenchSessions.MainToolbar.Activity"
    }

    object TreePopup {
      const val GROUP: String = "AgentWorkbenchSessions.TreePopup"
      const val EMPTY_AREA_GROUP: String = "AgentWorkbenchSessions.TreePopup.EmptyArea"
      const val NEW_THREAD: String = "AgentWorkbenchSessions.TreePopup.NewThread"
      const val ARCHIVE: String = "AgentWorkbenchSessions.TreePopup.Archive"
    }

    object ThreadOutline {
      const val POPUP_GROUP: String = "AgentWorkbenchSessions.ThreadOutline.Popup"
      const val START_NEW_THREAD_FROM_HERE: String = "AgentWorkbenchSessions.ThreadOutline.StartNewThreadFromHere"
    }
  }
}
