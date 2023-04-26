// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
import com.intellij.collaboration.ui.toolwindow.manageReviewToolwindowTabs
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContextHolder

internal class GitLabToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    val repositoriesManager = toolWindow.project.service<GitLabProjectsManager>()
    DisposingMainScope(toolWindow.disposable).launch {
      repositoriesManager.knownRepositoriesState.collect {
        toolWindow.isAvailable = it.isNotEmpty()
      }
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    toolWindow.dontHideOnEmptyContent()

    val cs = toolWindow.contentManager.disposingMainScope()
    val contextHolder = project.service<GitLabProjectUIContextHolder>()

    val tabsController = GitLabReviewTabsController()
    val componentFactory = GitLabReviewTabComponentFactory(project, contextHolder)

    manageReviewToolwindowTabs(cs, toolWindow, contextHolder, tabsController, componentFactory)

    toolWindow.setAdditionalGearActions(DefaultActionGroup(GitLabSwitchProjectAndAccountAction()))
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}