// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.SHELF
import org.jetbrains.annotations.NonNls

internal class VcsShowLocalChangesAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = LOCAL_CHANGES
}

internal class VcsShowShelfAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = SHELF

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project ?: return
    if (ShelvedChangesViewManager.hideDefaultShelfTab(project)) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}

abstract class VcsShowToolWindowTabAction : DumbAwareAction() {
  protected abstract val tabName: String

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
                                         ChangesViewContentManager.getToolWindowFor(project, tabName) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    activateVcsTab(project, tabName, true)
  }

  companion object {
    @JvmStatic
    fun activateVcsTab(project: Project, tabName: @NonNls String, isToggle: Boolean) {
      val toolWindow = ChangesViewContentManager.getToolWindowFor(project, tabName) ?: return
      val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager

      val isToolWindowActive = toolWindow.isActive
      val isContentSelected = contentManager.isContentSelected(tabName)
      val tabSelector = Runnable { contentManager.selectContent(tabName, true) }

      when {
        isToolWindowActive && isContentSelected -> {
          if (isToggle) {
            toolWindow.hide(null)
          }
          else {
            tabSelector.run()
          }
        }
        isToolWindowActive && !isContentSelected -> tabSelector.run()
        !isToolWindowActive && isContentSelected -> toolWindow.activate(null, true)
        else -> toolWindow.activate(tabSelector, false)
      }
    }
  }
}
