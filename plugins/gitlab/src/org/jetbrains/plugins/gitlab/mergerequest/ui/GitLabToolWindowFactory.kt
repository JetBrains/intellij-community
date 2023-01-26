// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager

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

    GitLabToolwindowTabsManager.showGitLabToolwindowContent(project, toolWindow.contentManager)
    // TODO: introduce account changing action under gear
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}