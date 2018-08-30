// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.panels.Wrapper
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsChangesModel
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import org.jetbrains.plugins.github.util.GithubUrlUtil
import javax.swing.JComponent


class GithubPullRequestsComponentFactory(private val project: Project,
                                         private val progressManager: ProgressManager,
                                         private val requestExecutorManager: GithubApiRequestExecutorManager,
                                         private val git: Git,
                                         private val actionManager: ActionManager,
                                         private val autoPopupController: AutoPopupController,
                                         private val popupFactory: JBPopupFactory) {

  fun createComponent(repository: GitRepository, remote: GitRemote, remoteUrl: String, account: GithubAccount): JComponent? {

    val requestExecutorHolder = requestExecutorManager.getManagedHolder(account, project) ?: return null
    val loader = GithubPullRequestsLoader(progressManager, requestExecutorHolder,
                                          account.server, GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl)!!)
    val list = GithubPullRequestsListComponent(project, actionManager, autoPopupController, popupFactory, loader)

    val changesModel = GithubPullRequestsChangesModel(project, progressManager, requestExecutorHolder, git,
                                                      repository, remote)
    val changes = GithubPullRequestChangesComponent(project, changesModel)
    list.addSelectionListener(changesModel, list)
    list.setToolbarHeightReferent(changes.toolbarComponent)

    val splitter = OnePixelSplitter("Github.PullRequests.Component", 0.7f)
    splitter.firstComponent = list
    splitter.secondComponent = changes

    // disposed by content manager when tab is closed
    val wrapper = DisposableWrapper(splitter)
    Disposer.register(wrapper, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(changes)

      Disposer.dispose(loader)
      Disposer.dispose(changesModel)

      Disposer.dispose(requestExecutorHolder)
    })
    return wrapper
  }

  companion object {
    private class DisposableWrapper(wrapped: JComponent) : Wrapper(wrapped), Disposable {
      init {
        isFocusCycleRoot = true
      }

      override fun dispose() {}
    }
  }
}