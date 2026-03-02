// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

object AgentWorkbenchActionIds {
  object Sessions {
    const val OPEN_DEDICATED_FRAME: String = "AgentWorkbenchSessions.OpenDedicatedFrame"
    const val GO_TO_SOURCE_PROJECT_FROM_TOOLBAR: String = "AgentWorkbenchSessions.GoToSourceProjectFromToolbar"
    const val TOOL_WINDOW_GEAR_ACTIONS: String = "AgentWorkbenchSessions.ToolWindow.GearActions"
    const val TOGGLE_DEDICATED_FRAME: String = "AgentWorkbenchSessions.ToggleDedicatedFrame"
    const val TOGGLE_CLAUDE_QUOTA_WIDGET: String = "AgentWorkbenchSessions.ToggleClaudeQuotaWidget"
    const val REFRESH: String = "AgentWorkbenchSessions.Refresh"
    const val ARCHIVE_THREAD_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.ArchiveThreadFromEditorTab"
    const val GO_TO_SOURCE_PROJECT_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.GoToSourceProjectFromEditorTab"
    const val BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab"
    const val EDITOR_TAB_POPUP_SEPARATOR_BEFORE_CLOSE_ACTIONS: String =
      "AgentWorkbenchSessions.EditorTabPopup.SeparatorBeforeCloseActions"
    const val COPY_THREAD_ID_FROM_EDITOR_TAB: String = "AgentWorkbenchSessions.CopyThreadIdFromEditorTab"
    const val SELECT_THREAD_IN_AGENT_THREADS: String = "AgentWorkbenchSessions.SelectThreadInAgentThreads"
    const val ACTIVATE_WITH_PROJECT_SHORTCUT: String = "AgentWorkbenchSessions.ActivateWithProjectShortcut"

    object TreePopup {
      const val GROUP: String = "AgentWorkbenchSessions.TreePopup"
      const val OPEN: String = "AgentWorkbenchSessions.TreePopup.Open"
      const val MORE: String = "AgentWorkbenchSessions.TreePopup.More"
      const val NEW_THREAD: String = "AgentWorkbenchSessions.TreePopup.NewThread"
      const val ARCHIVE: String = "AgentWorkbenchSessions.TreePopup.Archive"
    }

    object EditorTab {
      const val NEW_THREAD_QUICK: String = "AgentWorkbenchSessions.EditorTab.NewThreadQuick"
      const val NEW_THREAD_POPUP: String = "AgentWorkbenchSessions.EditorTab.NewThreadPopup"
    }
  }
}
