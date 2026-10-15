// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectBrancher
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.projectNioPath
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** Shows the branches of a recent version control project from the welcome screen, without opening the project. */
internal class BranchesRecentProjectAction : RecentProjectsWelcomeScreenActionBase() {
  private val recentProjectsService = RecentProjectsService.getInstance()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val projectPath = selectedProjectPath(event)
    event.presentation.isEnabledAndVisible = projectPath != null && RecentProjectBrancher.findBrancher(projectPath) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val projectPath = selectedProjectPath(event) ?: return
    val anchor = welcomeScreenAnchor(event)
    val brancher = RecentProjectBrancher.findBrancher(projectPath)
    recentProjectsService.runOnRecentProject(projectPath) { project ->
      if (brancher != null) {
        brancher.showBranches(project, projectPath, anchor)
      }
      else {
        withContext(Dispatchers.UiWithModelAccess) {
          val popup = JBPopupFactory.getInstance()
            .createMessage(IdeBundle.message("welcome.screen.recent.projects.popup.content.no.vcs.repository.in.project"))
          if (anchor != null) {
            popup.show(anchor)
          }
          else {
            popup.showInFocusCenter()
          }
        }
      }
    }
  }

  private fun selectedProjectPath(event: AnActionEvent): Path? =
    (getSelectedItem(event) as? RecentProjectItem)?.projectNioPath()

  /**
   * The place on the welcome screen where the branches popup opens: under the button that ran the action, or at the selected row when the
   * action was run from somewhere without a point of its own, such as a shortcut.
   *
   * It is taken while the tree is still the action context, because the project is loaded first and the popup opens only once it is.
   */
  private fun welcomeScreenAnchor(event: AnActionEvent): RelativePoint? {
    val tree = getTree(event)?.takeIf { it.isShowing } ?: return null
    val point = event.getData(PlatformDataKeys.CONTEXT_MENU_POINT) ?: return JBPopupFactory.getInstance().guessBestPopupLocation(tree)
    return RelativePoint(tree, point)
  }
}
