// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.DisposingScope
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRSelectPullRequestForFileAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRSwitchRemoteAction
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabControllerImpl
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import javax.swing.JPanel

internal class GHPRToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Pull Requests"
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.project.coroutineScope.launch {
      val repositoriesManager = toolWindow.project.service<GHHostedRepositoriesManager>()
      withContext<Unit>(Dispatchers.EDT) {
        repositoriesManager.knownRepositoriesState.collect {
          toolWindow.isAvailable = it.isNotEmpty()
        }
      }
    }.cancelOnDispose(toolWindow.disposable)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    configureToolWindow(toolWindow)
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(JPanel(null), null, false).apply {
      isCloseable = false
      setDisposer(Disposer.newDisposable("reviews tab disposable"))
    }
    configureContent(project, content)
    contentManager.addContent(content)
  }

  private fun configureToolWindow(toolWindow: ToolWindow) {
    toolWindow.setTitleActions(listOf(
      EmptyAction.registerWithShortcutSet("Github.Create.Pull.Request", CommonShortcuts.getNew(), toolWindow.component),
      GHPRSelectPullRequestForFileAction(),
    ))
    toolWindow.setAdditionalGearActions(DefaultActionGroup(GHPRSwitchRemoteAction()))
  }

  private fun configureContent(project: Project, content: Content) {
    val scope = DisposingScope(content)
    val repositoriesManager = project.service<GHHostedRepositoriesManager>()
    val connectionManager = project.service<GHRepositoryConnectionManager>()
    val accountManager = service<GHAccountManager>()
    val vm = GHPRToolWindowTabViewModel(scope, repositoriesManager, accountManager, connectionManager, project.service())

    val controller = GHPRToolWindowTabControllerImpl(scope, project, vm, content)
    content.putUserData(GHPRToolWindowTabController.KEY, controller)
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}