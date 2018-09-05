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
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBranchesFetcher
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
    val selectionModel = GithubPullRequestsListSelectionModel()
    val list = GithubPullRequestsListComponent(project, actionManager, autoPopupController, popupFactory, selectionModel, listLoader)

    val detailsLoader = GithubPullRequestsDetailsLoader(progressManager, requestExecutorHolder, selectionModel)
    val branchFetcher = GithubPullRequestsBranchesFetcher(progressManager, git, detailsLoader, repository, remote)
    val changesLoader = GithubPullRequestsChangesLoader(project, progressManager, branchFetcher, repository)

    val changes = GithubPullRequestChangesComponent(project, changesLoader)
    list.setToolbarHeightReferent(changes.toolbarComponent)

    val splitter = OnePixelSplitter("Github.PullRequests.Component", 0.7f)
    splitter.firstComponent = list
    splitter.secondComponent = changes

    // disposed by content manager when tab is closed
    val wrapper = WrappingComponent(splitter, repository, remote, repoPath, account, listLoader, detailsLoader, branchFetcher)
    Disposer.register(wrapper, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(changes)

      Disposer.dispose(listLoader)
      Disposer.dispose(changesLoader)
      Disposer.dispose(branchFetcher)
      Disposer.dispose(detailsLoader)

      Disposer.dispose(requestExecutorHolder)
    })
    return wrapper
  }

  companion object {
    private class WrappingComponent(wrapped: JComponent,
                                    private val repository: GitRepository,
                                    private val remote: GitRemote,
                                    private val repoPath: GithubFullPath,
                                    private val account: GithubAccount,
                                    private val listLoader: GithubPullRequestsLoader,
                                    private val detailsLoader: GithubPullRequestsDetailsLoader,
                                    private val branchesFetcher: GithubPullRequestsBranchesFetcher)
      : Wrapper(wrapped), Disposable, DataProvider {
      init {
        isFocusCycleRoot = true
      }

      override fun getData(dataId: String): Any? {
        return when {
          GithubPullRequestKeys.REPOSITORY.`is`(dataId) -> repository
          GithubPullRequestKeys.REMOTE.`is`(dataId) -> remote
          GithubPullRequestKeys.FULL_PATH.`is`(dataId) -> repoPath
          GithubPullRequestKeys.SERVER_PATH.`is`(dataId) -> account.server
          GithubPullRequestKeys.PULL_REQUESTS_LOADER.`is`(dataId) -> listLoader
          GithubPullRequestKeys.PULL_REQUESTS_DETAILS_LOADER.`is`(dataId) -> detailsLoader
          GithubPullRequestKeys.PULL_REQUESTS_BRANCHES_FETCHER.`is`(dataId) -> branchesFetcher
          else -> null
        }
      }

      override fun dispose() {}
    }
  }
}