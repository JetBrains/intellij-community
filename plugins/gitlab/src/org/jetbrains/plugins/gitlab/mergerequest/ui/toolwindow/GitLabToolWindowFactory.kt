// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.toolwindow.manageReviewToolwindowTabs
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager

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

    val cs = toolWindow.contentManager.disposingMainScope()
    val projectContext = project.service<GitLabProjectConnectionManager>().connectionState.mapState(cs) { connection ->
      connection?.let { GitLabToolwindowProjectContext(it) }
    }

    val tabsController = GitLabReviewTabsController(project)
    val componentFactory = GitLabReviewTabComponentFactory(project)

    manageReviewToolwindowTabs(toolWindow.contentManager, projectContext, tabsController, componentFactory)
    // TODO: introduce account changing action under gear
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}