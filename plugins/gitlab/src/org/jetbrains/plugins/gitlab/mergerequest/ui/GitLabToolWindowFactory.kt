// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import com.intellij.util.childScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.JPanel

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
    with(toolWindow.contentManager) {
      val content = factory.createContent(JPanel(null), null, false).apply {
        isCloseable = false
      }
      configureContent(project, content)
      addContent(content)
    }
  }

  private fun configureContent(project: Project, content: Content) {
    val scope = DisposingMainScope(content)
    val projectsManager = project.service<GitLabProjectsManager>()
    val accountManager = service<GitLabAccountManager>()

    val connectionManager = GitLabProjectConnectionManager(projectsManager, accountManager)

    val vm = GitLabToolWindowTabViewModel(scope, connectionManager, projectsManager, accountManager)

    GitLabToolWindowTabController(project, scope.childScope(), vm, content)
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}