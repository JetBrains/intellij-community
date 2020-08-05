// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.plugins.github.pullrequest.action.GHPRViewFilePullRequestAction

class GHPRToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
    toolWindow.setTitleActions(listOf(GHPRViewFilePullRequestAction()))
    project.service<GHPRToolWindowTabsManager>().contentManager = GHPRToolWindowTabsContentManager(project, toolWindow.contentManager)
  }

  override fun shouldBeAvailable(project: Project): Boolean = invokeAndWaitIfNeeded { project.service<GHPRToolWindowTabsManager>().isAvailable() }

  companion object {
    const val ID = "Pull Requests"
  }
}
