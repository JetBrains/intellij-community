// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRSelectPullRequestForFileAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRSwitchRemoteAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabControllerImpl
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import javax.swing.JPanel


class GHPRToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = with(toolWindow as ToolWindowEx) {
    setTitleActions(listOf(EmptyAction.registerWithShortcutSet("Github.Create.Pull.Request", CommonShortcuts.getNew(), component),
                           GHPRSelectPullRequestForFileAction()))
    setAdditionalGearActions(DefaultActionGroup(GHPRSwitchRemoteAction()))
    component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    with(contentManager) {
      addContent(factory.createContent(JPanel(null), null, false).apply {
        isCloseable = false
        setDisposer(Disposer.newDisposable("GHPR tab disposable"))
      }.also {
        val authManager = GithubAuthenticationManager.getInstance()
        val repositoryManager = project.service<GHProjectRepositoriesManager>()
        val dataContextRepository = GHPRDataContextRepository.getInstance(project)
        val projectString = GithubPullRequestsProjectUISettings.getInstance(project)
        it.putUserData(GHPRToolWindowTabController.KEY,
                       GHPRToolWindowTabControllerImpl(project, authManager, repositoryManager, dataContextRepository, projectString, it))
      })
    }
  }

  override fun shouldBeAvailable(project: Project): Boolean = false

  companion object {
    const val ID = "Pull Requests"
  }
}