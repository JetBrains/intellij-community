// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
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
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsChangesLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDetailsLoader
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
    val repoPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl)!!
    val listLoader = GithubPullRequestsLoader(progressManager, requestExecutorHolder,
                                              account.server, repoPath)
    val detailsLoader = GithubPullRequestsDetailsLoader(progressManager, requestExecutorHolder, git, repository, remote)
    val parametersDataProvider = DataProvider {
      when {
        GithubPullRequestKeys.REPOSITORY.`is`(it) -> repository
        GithubPullRequestKeys.REMOTE.`is`(it) -> remote
        GithubPullRequestKeys.FULL_PATH.`is`(it) -> repoPath
        GithubPullRequestKeys.SERVER_PATH.`is`(it) -> account.server
        else -> null
      }
    }
    val list = GithubPullRequestsListComponent(project, actionManager, autoPopupController, popupFactory,
                                               parametersDataProvider, detailsLoader, listLoader)
    val changesLoader = GithubPullRequestsChangesLoader(project, progressManager, detailsLoader, repository)
    val changes = GithubPullRequestChangesComponent(project, changesLoader)
    list.addSelectionListener(changesLoader, list)
    list.setToolbarHeightReferent(changes.toolbarComponent)

    val splitter = OnePixelSplitter("Github.PullRequests.Component", 0.7f)
    splitter.firstComponent = list
    splitter.secondComponent = changes

    // disposed by content manager when tab is closed
    val wrapper = DisposableWrapper(splitter)
    Disposer.register(wrapper, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(changes)

      Disposer.dispose(listLoader)
      Disposer.dispose(changesLoader)
      Disposer.dispose(detailsLoader)

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