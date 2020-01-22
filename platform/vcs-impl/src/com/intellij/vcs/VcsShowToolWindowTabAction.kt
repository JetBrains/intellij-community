// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.SHELF
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.wm.ToolWindow

class VcsShowLocalChangesAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = LOCAL_CHANGES
}

class VcsShowShelfAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = SHELF
}

abstract class VcsShowToolWindowTabAction : DumbAwareAction() {
  protected abstract val tabName: String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getToolWindow(e.project) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val toolWindow = getToolWindow(project)!!
    val changesViewContentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager

    if (toolWindow.isActive && changesViewContentManager.isContentSelected(tabName)) {
      toolWindow.hide(null)
    }
    else {
      toolWindow.activate(
        {
          if (!changesViewContentManager.isContentSelected(tabName)) {
            changesViewContentManager.selectContent(tabName, true)
          }
        }, true, true
      )
    }
  }

  private fun getToolWindow(project: Project?): ToolWindow? = project?.let { getToolWindowFor(it, tabName) }
}