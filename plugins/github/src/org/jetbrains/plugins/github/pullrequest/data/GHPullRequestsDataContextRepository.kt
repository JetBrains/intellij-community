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
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PULL_REQUEST_EDITED_TOPIC
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PullRequestEditedListener
import org.jetbrains.plugins.github.pullrequest.data.service.*
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
    val repoWithPermissions =
      requestExecutor.execute(indicator, GHGQLRequests.Repo.findPermission(GHRepositoryCoordinates(account.server, fullPath)))
      ?: throw IllegalArgumentException("Repository $fullPath does not exist at ${account.server} or you don't have access.")

    val currentUser = GHUser(accountDetails.nodeId, accountDetails.login, accountDetails.htmlUrl, accountDetails.avatarUrl!!,
                             accountDetails.name)
    val repositoryCoordinates = GHRepositoryCoordinates(account.server, repoWithPermissions.path)

    val messageBus = messageBusFactory.createMessageBus(this)

    val securityService = GithubPullRequestsSecurityServiceImpl(sharedProjectSettings, currentUser, repoWithPermissions)
    val reviewService = GHPRReviewServiceImpl(progressManager, messageBus, securityService, requestExecutor, repositoryCoordinates)
    val commentService = GHPRCommentServiceImpl(progressManager, messageBus, securityService, requestExecutor, repositoryCoordinates)

    val listModel = CollectionListModel<GHPullRequestShort>()
    val searchHolder = GithubPullRequestSearchQueryHolderImpl()
    val listLoader = GHPRListLoaderImpl(progressManager, requestExecutor, account.server, repoWithPermissions.path, listModel,
                                        searchHolder)

    val dataLoader = GithubPullRequestsDataLoaderImpl {
      GithubPullRequestDataProviderImpl(project, progressManager, git, requestExecutor, gitRemoteCoordinates, repositoryCoordinates, it)
    }
    messageBus.connect().subscribe(PULL_REQUEST_EDITED_TOPIC, object : PullRequestEditedListener {
      override fun onPullRequestEdited(number: Long) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(number)
          dataProvider?.reloadDetails()
          dataProvider?.detailsRequest?.let { listLoader.reloadData(it) }
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }

      override fun onPullRequestReviewsEdited(number: Long) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(number)
          dataProvider?.reloadReviewThreads()
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }

      override fun onPullRequestCommentsEdited(number: Long) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(number)
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }
    })
    val busyStateTracker = GithubPullRequestsBusyStateTrackerImpl()
    val metadataService = GithubPullRequestsMetadataServiceImpl(progressManager, messageBus, requestExecutor, account.server,
                                                                repoWithPermissions.path)
    val stateService = GithubPullRequestsStateServiceImpl(progressManager, messageBus,
                                                          requestExecutor, account.server, repoWithPermissions.path)

    return GHPullRequestsDataContext(gitRemoteCoordinates, repositoryCoordinates, account,
                                     requestExecutor, messageBus, listModel, searchHolder, listLoader, dataLoader, securityService,
                                     busyStateTracker, metadataService, stateService, reviewService, commentService)
  }

  companion object {
    fun getInstance(project: Project) = project.service<GHPullRequestsDataContextRepository>()
  }
}