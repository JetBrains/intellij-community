// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestChangesComponent
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestDetailsComponent
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestPreviewComponent
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListComponent
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionModel.SelectionChangedListener
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import javax.swing.JComponent


internal class GithubPullRequestsComponentFactory(private val project: Project,
                                                  private val copyPasteManager: CopyPasteManager,
                                                  private val progressManager: ProgressManager,
                                                  private val git: Git,
                                                  private val avatarLoader: CachingGithubUserAvatarLoader,
                                                  private val imageResizer: GithubImageResizer,
                                                  private val actionManager: ActionManager,
                                                  private val autoPopupController: AutoPopupController) {

  fun createComponent(requestExecutor: GithubApiRequestExecutor,
                      repository: GitRepository, remote: GitRemote,
                      repoDetails: GithubRepoDetailed,
                      account: GithubAccount): JComponent? {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, requestExecutor)
    return GithubPullRequestsComponent(requestExecutor, avatarIconsProviderFactory, repository, remote, repoDetails, account)
  }

  inner class GithubPullRequestsComponent(private val requestExecutor: GithubApiRequestExecutor,
                                          avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                          private val repository: GitRepository, private val remote: GitRemote,
                                          private val repoDetails: GithubRepoDetailed,
                                          private val account: GithubAccount)
    : OnePixelSplitter("Github.PullRequests.Component", 0.33f), Disposable, DataProvider {

    private val dataLoader = GithubPullRequestsDataLoader(project, progressManager, git, requestExecutor, repository, remote)

    private val changes = GithubPullRequestChangesComponent(project).apply {
      diffAction.registerCustomShortcutSet(this@GithubPullRequestsComponent, this@GithubPullRequestsComponent)
    }
    private val details = GithubPullRequestDetailsComponent(avatarIconsProviderFactory)
    private val preview = GithubPullRequestPreviewComponent(changes, details)

    private val listLoader = GithubPullRequestsLoader(progressManager, requestExecutor, account.server, repoDetails.fullPath)
    private val list = GithubPullRequestsListComponent(project, copyPasteManager, actionManager, autoPopupController,
                                                       listLoader,
                                                       avatarIconsProviderFactory).apply {
      requestExecutor.addListener(this) { this.refresh() }
    }


    init {
      firstComponent = list
      secondComponent = preview
      isFocusCycleRoot = true

      list.selectionModel.addChangesListener(object : SelectionChangedListener {
        override fun selectionChanged() {
          val dataProvider = list.selectionModel.current?.let(dataLoader::getDataProvider)
          preview.setPreviewDataProvider(dataProvider)
        }
      }, preview)

      dataLoader.addProviderChangesListener(object : GithubPullRequestsDataLoader.ProviderChangedListener {
        override fun providerChanged(pullRequestNumber: Long) {
          runInEdt {
            if (Disposer.isDisposed(preview)) return@runInEdt
            val selection = list.selectionModel.current
            if (selection != null && selection.number == pullRequestNumber) {
              preview.setPreviewDataProvider(dataLoader.getDataProvider(selection))
            }
          }
        }
      }, preview)
    }

    @CalledInAwt
    fun refreshAllPullRequests() {
      list.refresh()
      dataLoader.invalidateAllData()
    }

    //TODO: refresh in list
    @CalledInAwt
    fun refreshPullRequest(number: Long) {
      dataLoader.invalidateData(number)
    }

    override fun getData(dataId: String): Any? {
      if (Disposer.isDisposed(this)) return null
      return when {
        GithubPullRequestKeys.REPOSITORY.`is`(dataId) -> repository
        GithubPullRequestKeys.REMOTE.`is`(dataId) -> remote
        GithubPullRequestKeys.REPO_DETAILS.`is`(dataId) -> repoDetails
        GithubPullRequestKeys.SERVER_PATH.`is`(dataId) -> account.server
        GithubPullRequestKeys.API_REQUEST_EXECUTOR.`is`(dataId) -> requestExecutor
        GithubPullRequestKeys.PULL_REQUESTS_COMPONENT.`is`(dataId) -> this
        GithubPullRequestKeys.SELECTED_PULL_REQUEST.`is`(dataId) -> list.selectionModel.current
        GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER.`is`(dataId) ->
          list.selectionModel.current?.let(dataLoader::getDataProvider)
        else -> null
      }
    }

    override fun dispose() {
      Disposer.dispose(list)
      Disposer.dispose(preview)
      Disposer.dispose(changes)
      Disposer.dispose(details)

      Disposer.dispose(listLoader)
      Disposer.dispose(dataLoader)
    }
  }
}