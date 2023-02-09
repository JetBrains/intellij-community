// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.ClientProperty
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRSelectPullRequestForFileAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRSwitchRemoteAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowContentController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.MultiTabGHPRToolWindowContentController
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

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
    with(toolWindow as ToolWindowEx) {
      component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
      setTitleActions(listOf(
        EmptyAction.registerWithShortcutSet("Github.Create.Pull.Request", CommonShortcuts.getNew(), toolWindow.component),
        GHPRSelectPullRequestForFileAction(),
      ))
      setAdditionalGearActions(DefaultActionGroup(GHPRSwitchRemoteAction()))

      // so it's not closed when all content is removed
      dontHideOnEmptyContent()
    }

    val controller = MultiTabGHPRToolWindowContentController(
      toolWindow.disposable, project,
      project.service<GHHostedRepositoriesManager>(),
      service<GHAccountManager>(),
      project.service<GHRepositoryConnectionManager>(),
      project.service<GithubPullRequestsProjectUISettings>(),
      toolWindow
    )

    ClientProperty.put(toolWindow.component, GHPRToolWindowContentController.KEY, controller)
  }

  override fun shouldBeAvailable(project: Project): Boolean = false
}