// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
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
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsUISettings
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUrlUtil
import javax.swing.JComponent


class GithubPullRequestsComponentFactory(private val project: Project,
                                         private val progressManager: ProgressManager,
                                         private val requestExecutorManager: GithubApiRequestExecutorManager,
                                         private val git: Git,
                                         private val uiSettings: GithubPullRequestsUISettings,
                                         private val avatarLoader: CachingGithubUserAvatarLoader,
                                         private val imageResizer: GithubImageResizer,
                                         private val actionManager: ActionManager,
                                         private val autoPopupController: AutoPopupController) {

  fun createComponent(repository: GitRepository, remote: GitRemote, remoteUrl: String, account: GithubAccount): JComponent? {

    val requestExecutor = requestExecutorManager.getExecutor(account, project) ?: return null
    val repoPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl)!!
    val listLoader = GithubPullRequestsLoader(progressManager, requestExecutor,
                                              account.server, repoPath)
    val selectionModel = GithubPullRequestsListSelectionModel()
    val list = GithubPullRequestsListComponent(project, actionManager, autoPopupController,
                                               selectionModel, listLoader,
                                               CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, requestExecutor))
    requestExecutor.addListener(list) { list.refresh() }

    val dataLoader = GithubPullRequestsDataLoader(project, progressManager, git, requestExecutor, repository, remote)

    val changes = GithubPullRequestChangesComponent(project, selectionModel, dataLoader, actionManager)
    val details = GithubPullRequestDetailsComponent(project, selectionModel, dataLoader)

    val preview = GithubPullRequestPreviewComponent(uiSettings, changes, details)
    list.setToolbarHeightReferent(preview.toolbarComponent)

    val splitter = OnePixelSplitter("Github.PullRequests.Component", 0.6f)
    splitter.firstComponent = list
    splitter.secondComponent = preview

    // disposed by content manager when tab is closed
    val wrapper = WrappingComponent(splitter,
                                    repository, remote, repoPath, account,
                                    list, selectionModel, dataLoader)
    Disposer.register(wrapper, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(preview)

      Disposer.dispose(listLoader)
      Disposer.dispose(dataLoader)
    })
    changes.diffAction.registerCustomShortcutSet(wrapper, wrapper)
    return wrapper
  }

  companion object {
    private class WrappingComponent(wrapped: JComponent,
                                    private val repository: GitRepository,
                                    private val remote: GitRemote,
                                    private val repoPath: GithubFullPath,
                                    private val account: GithubAccount,
                                    private val list: GithubPullRequestsListComponent,
                                    private val selectionModel: GithubPullRequestsListSelectionModel,
                                    private val dataLoader: GithubPullRequestsDataLoader)
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
          GithubPullRequestKeys.PULL_REQUESTS_LIST_COMPONENT.`is`(dataId) -> list
          GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> selectionModel.current?.let(dataLoader::getDataProvider)
          else -> null
        }
      }

      override fun dispose() {}
    }
  }
}