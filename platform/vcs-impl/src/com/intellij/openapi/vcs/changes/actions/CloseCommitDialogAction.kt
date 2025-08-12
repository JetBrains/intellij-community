// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow

/**
 * Closes the Commit toolwindow in floating or windowed view modes
 */
internal class CloseCommitDialogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      Registry.`is`("vcs.commit.dialog.esc.close") &&
      e.getCommitDialog() != null &&
      // Don't close the dialog if the speed search is active
      e.getData(PlatformDataKeys.SPEED_SEARCH_COMPONENT) == null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getCommitDialog()?.hide()
  }
}

private fun AnActionEvent.getCommitDialog(): ToolWindow? = getData(PlatformDataKeys.TOOL_WINDOW)?.takeIf {
  getData(ChangesViewContentManager.IS_COMMIT_TOOLWINDOW_WINDOWED_KEY) == true
}
