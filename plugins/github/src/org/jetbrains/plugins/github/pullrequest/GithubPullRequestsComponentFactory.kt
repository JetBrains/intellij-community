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
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTrackerImpl
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsListLoaderImpl
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataServiceImpl
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityServiceImpl
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateServiceImpl
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import javax.swing.JComponent


internal class GithubPullRequestsComponentFactory(private val project: Project,
                                                  private val copyPasteManager: CopyPasteManager,
                                                  private val progressManager: ProgressManager,
                                                  private val git: Git,
                                                  private val avatarLoader: CachingGithubUserAvatarLoader,
                                                  private val imageResizer: GithubImageResizer,
                                                  private val actionManager: ActionManager,
                                                  private val autoPopupController: AutoPopupController,
                                                  private val sharedProjectSettings: GithubSharedProjectSettings,
                                                  private val pullRequestUiSettings: GithubPullRequestsProjectUISettings) {

  fun createComponent(requestExecutor: GithubApiRequestExecutor,
                      repository: GitRepository, remote: GitRemote,
                      accountDetails: GithubAuthenticatedUser, repoDetails: GithubRepoDetailed,
                      account: GithubAccount): JComponent? {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, requestExecutor)
    return GithubPullRequestsComponent(requestExecutor, avatarIconsProviderFactory, pullRequestUiSettings,
                                       repository, remote,
                                       accountDetails, repoDetails, account)
  }

  inner class GithubPullRequestsComponent(private val requestExecutor: GithubApiRequestExecutor,
                                          avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                          pullRequestUiSettings: GithubPullRequestsProjectUISettings,
                                          private val repository: GitRepository, private val remote: GitRemote,
                                          accountDetails: GithubAuthenticatedUser,
                                          private val repoDetails: GithubRepoDetailed,
                                          private val account: GithubAccount)
    : OnePixelSplitter("Github.PullRequests.Component", 0.33f), Disposable, DataProvider {

    private val dataLoader = GithubPullRequestsDataLoader(project, progressManager, git, requestExecutor, repository, remote,
                                                          account.server, repoDetails.fullPath)
    private val securityService = GithubPullRequestsSecurityServiceImpl(sharedProjectSettings, accountDetails, repoDetails)
    private val busyStateTracker = GithubPullRequestsBusyStateTrackerImpl()

    private val metadataService = GithubPullRequestsMetadataServiceImpl(project, progressManager, dataLoader, busyStateTracker,
                                                                        requestExecutor,
                                                                        avatarIconsProviderFactory, account.server, repoDetails.fullPath)
    private val stateService = GithubPullRequestsStateServiceImpl(project, progressManager, dataLoader, busyStateTracker, requestExecutor,
                                                                  account.server, repoDetails.fullPath)

    private val changes = GithubPullRequestChangesComponent(project, pullRequestUiSettings).apply {
      diffAction.registerCustomShortcutSet(this@GithubPullRequestsComponent, this@GithubPullRequestsComponent)
    }
    private val details = GithubPullRequestDetailsComponent(dataLoader, securityService, busyStateTracker, metadataService, stateService,
                                                            avatarIconsProviderFactory)
    private val preview = GithubPullRequestPreviewComponent(changes, details)

    private val listSelectionHolder = GithubPullRequestsListSelectionHolderImpl()
    private val listLoader = GithubPullRequestsListLoaderImpl(progressManager, requestExecutor, account.server, repoDetails.fullPath)
    private val list = GithubPullRequestsListWithSearchPanel(project, copyPasteManager, actionManager, autoPopupController,
                                                             avatarIconsProviderFactory,
                                                             listLoader, listLoader, listLoader, listSelectionHolder)


    init {
      firstComponent = list
      secondComponent = preview
      isFocusCycleRoot = true

      listSelectionHolder.addSelectionChangeListener(preview) {
        val dataProvider = listSelectionHolder.selection?.number?.let(dataLoader::getDataProvider)
        preview.setPreviewDataProvider(dataProvider)
      }

      dataLoader.addProviderChangesListener(object : GithubPullRequestsDataLoader.ProviderChangedListener {
        override fun providerChanged(pullRequestNumber: Long) {
          runInEdt {
            if (Disposer.isDisposed(preview)) return@runInEdt
            val selection = listSelectionHolder.selection
            if (selection != null && selection.number == pullRequestNumber) {
              preview.setPreviewDataProvider(dataLoader.getDataProvider(pullRequestNumber))
            }
          }
        }
      }, preview)
    }

    @CalledInAwt
    fun refreshAllPullRequests() {
      listLoader.reset()
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
        GithubPullRequestKeys.SELECTED_PULL_REQUEST.`is`(dataId) -> listSelectionHolder.selection
        GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER.`is`(dataId) ->
          listSelectionHolder.selection?.number?.let(dataLoader::getDataProvider)
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