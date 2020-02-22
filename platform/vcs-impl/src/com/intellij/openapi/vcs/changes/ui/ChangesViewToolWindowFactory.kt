// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ActivateToolWindowAction.getActionIdForToolWindow
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ui.ActionsTree.isShortcutCustomized
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.COMMIT_TOOLWINDOW_ID
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL

private class ChangesViewToolWindowFactory : VcsToolWindowFactory() {
  override fun updateState(project: Project, toolWindow: ToolWindow) {
    super.updateState(project, toolWindow)
    toolWindow.stripeTitle = project.vcsManager.allActiveVcss.singleOrNull()?.displayName ?: ToolWindowId.VCS
  }
}

private class CommitToolWindowFactory : VcsToolWindowFactory() {
  override fun shouldBeAvailable(project: Project): Boolean {
    return super.shouldBeAvailable(project) && project.isCommitToolWindow
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(HIDE_ID_LABEL, "true")
    super.createToolWindowContent(project, toolWindow)
  }
}

private class ActivateVersionControlToolWindowAction : ActivateToolWindowAction(ToolWindowId.VCS) {
  init {
    templatePresentation.text = toolWindowId
  }

  override fun useMnemonicFromShortcuts(project: Project): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(COMMIT_TOOLWINDOW_ID)?.isAvailable != true ||
           isShortcutCustomized(toolWindowId) ||
           isShortcutCustomized(COMMIT_TOOLWINDOW_ID)
  }
}

private fun isShortcutCustomized(toolWindowId: String): Boolean {
  return isShortcutCustomized(getActionIdForToolWindow(toolWindowId), KeymapManager.getInstance().activeKeymap)
}