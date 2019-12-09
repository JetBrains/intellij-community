// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.util.messages.MessageBusFactory
import git4idea.commands.Git
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PULL_REQUEST_EDITED_TOPIC
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PullRequestEditedListener
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataServiceImpl
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityServiceImpl
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateServiceImpl
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolderImpl
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import org.jetbrains.plugins.github.util.GithubUrlUtil
import java.io.IOException

@Service
internal class GHPullRequestsDataContextRepository(private val project: Project) {

  private val progressManager = ProgressManager.getInstance()
  private val messageBusFactory = MessageBusFactory.getInstance()
  private val git = Git.getInstance()
  private val accountInformationProvider = GithubAccountInformationProvider.getInstance()

  private val sharedProjectSettings = GithubSharedProjectSettings.getInstance(project)

  @CalledInBackground
  @Throws(IOException::class)
  fun getContext(indicator: ProgressIndicator,
                 account: GithubAccount, requestExecutor: GithubApiRequestExecutor,
                 gitRemoteCoordinates: GitRemoteUrlCoordinates): GHPullRequestsDataContext {
    val fullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(gitRemoteCoordinates.url)
                   ?: throw IllegalArgumentException(
                     "Invalid GitHub Repository URL - ${gitRemoteCoordinates.url} is not a GitHub repository")

    indicator.text = "Loading account information"
    val accountDetails = accountInformationProvider.getInformation(requestExecutor, indicator, account)
    indicator.checkCanceled()

    indicator.text = "Loading repository information"
    val repoDetails = requestExecutor.execute(indicator, GithubApiRequests.Repos.get(account.server, fullPath.owner, fullPath.repository))
                      ?: throw IllegalArgumentException(
                        "Repository $fullPath does not exist at ${account.server} or you don't have access.")

    val messageBus = messageBusFactory.createMessageBus(this)

    val listModel = CollectionListModel<GHPullRequestShort>()
    val searchHolder = GithubPullRequestSearchQueryHolderImpl()
    val listLoader = GHPRListLoaderImpl(progressManager, requestExecutor, account.server, repoDetails.fullPath, listModel, searchHolder)
    val dataLoader = GithubPullRequestsDataLoaderImpl(project, progressManager, git, requestExecutor,
                                                      gitRemoteCoordinates.repository, gitRemoteCoordinates.remote,
                                                      account.server, repoDetails.fullPath)
    messageBus.connect().subscribe(PULL_REQUEST_EDITED_TOPIC, object : PullRequestEditedListener {
      override fun onPullRequestEdited(number: Long) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(number)
          dataProvider?.reloadDetails()
          dataProvider?.detailsRequest?.let { listLoader.reloadData(it) }
        }
      }
    })
    val securityService = GithubPullRequestsSecurityServiceImpl(sharedProjectSettings, accountDetails, repoDetails)
    val busyStateTracker = GithubPullRequestsBusyStateTrackerImpl()
    val metadataService = GithubPullRequestsMetadataServiceImpl(progressManager, messageBus, requestExecutor, account.server,
                                                                repoDetails.fullPath)
    val stateService = GithubPullRequestsStateServiceImpl(project, progressManager, messageBus, dataLoader,
                                                          busyStateTracker,
                                                          requestExecutor, account.server, repoDetails.fullPath)

    return GHPullRequestsDataContext(gitRemoteCoordinates, GHRepositoryCoordinates(account.server, repoDetails.fullPath), account,
                                     requestExecutor, messageBus, listModel, searchHolder, listLoader, dataLoader, securityService,
                                     busyStateTracker, metadataService, stateService)
  }

  companion object {
    fun getInstance(project: Project) = project.service<GHPullRequestsDataContextRepository>()
  }
}