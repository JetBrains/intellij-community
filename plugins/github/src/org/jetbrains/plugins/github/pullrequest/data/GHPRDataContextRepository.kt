// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRDiffRequestModelImpl
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchQueryHolderImpl
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.io.IOException
import java.util.concurrent.CompletableFuture

@Service
internal class GHPRDataContextRepository(private val project: Project) {

  private val repositories = mutableMapOf<GHRepositoryCoordinates, LazyCancellableBackgroundProcessValue<GHPRDataContext>>()

  @RequiresEdt
  fun acquireContext(repository: GHRepositoryCoordinates, remote: GitRemoteUrlCoordinates,
                     account: GithubAccount, requestExecutor: GithubApiRequestExecutor): CompletableFuture<GHPRDataContext> {

    return repositories.getOrPut(repository) {
      val contextDisposable = Disposer.newDisposable()
      LazyCancellableBackgroundProcessValue.create { indicator ->
        ProgressManager.getInstance().submitIOTask(indicator) {
          try {
            loadContext(indicator, account, requestExecutor, repository, remote)
          }
          catch (e: Exception) {
            if (e !is ProcessCanceledException) LOG.info("Error occurred while creating data context", e)
            throw e
          }
        }.successOnEdt { ctx ->
          if (Disposer.isDisposed(contextDisposable)) {
            Disposer.dispose(ctx)
          }
          else {
            Disposer.register(contextDisposable, ctx)
          }
          ctx
        }
      }.also {
        it.addDropEventListener {
          Disposer.dispose(contextDisposable)
        }
      }
    }.value
  }

  @RequiresEdt
  fun clearContext(repository: GHRepositoryCoordinates) {
    repositories.remove(repository)?.drop()
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  private fun loadContext(indicator: ProgressIndicator,
                          account: GithubAccount,
                          requestExecutor: GithubApiRequestExecutor,
                          parsedRepositoryCoordinates: GHRepositoryCoordinates,
                          remoteCoordinates: GitRemoteUrlCoordinates): GHPRDataContext {
    indicator.text = GithubBundle.message("pull.request.loading.account.info")
    val accountDetails = GithubAccountInformationProvider.getInstance().getInformation(requestExecutor, indicator, account)
    indicator.checkCanceled()

    indicator.text = GithubBundle.message("pull.request.loading.repo.info")
    val repositoryInfo =
      requestExecutor.execute(indicator, GHGQLRequests.Repo.find(GHRepositoryCoordinates(account.server,
                                                                                         parsedRepositoryCoordinates.repositoryPath)))
      ?: throw IllegalArgumentException(
        "Repository ${parsedRepositoryCoordinates.repositoryPath} does not exist at ${account.server} or you don't have access.")

    val currentUser = GHUser(accountDetails.nodeId, accountDetails.login, accountDetails.htmlUrl, accountDetails.avatarUrl!!,
                             accountDetails.name)

    indicator.text = GithubBundle.message("pull.request.loading.user.teams.info")
    val repoOwner = repositoryInfo.owner
    val currentUserTeams = if (repoOwner is GHRepositoryOwnerName.Organization)
      SimpleGHGQLPagesLoader(requestExecutor, {
        GHGQLRequests.Organization.Team.findByUserLogins(account.server, repoOwner.login, listOf(currentUser.login), it)
      }).loadAll(indicator)
    else emptyList()
    indicator.checkCanceled()

    // repository might have been renamed/moved
    val apiRepositoryPath = repositoryInfo.path
    val apiRepositoryCoordinates = GHRepositoryCoordinates(account.server, apiRepositoryPath)

    val securityService = GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(project),
                                                  account, currentUser, currentUserTeams,
                                                  repositoryInfo)
    val detailsService = GHPRDetailsServiceImpl(ProgressManager.getInstance(), requestExecutor, apiRepositoryCoordinates)
    val stateService = GHPRStateServiceImpl(ProgressManager.getInstance(), securityService,
                                            requestExecutor, account.server, apiRepositoryPath)
    val commentService = GHPRCommentServiceImpl(ProgressManager.getInstance(), requestExecutor, apiRepositoryCoordinates)
    val changesService = GHPRChangesServiceImpl(ProgressManager.getInstance(), project, requestExecutor,
                                                remoteCoordinates, apiRepositoryCoordinates)
    val reviewService = GHPRReviewServiceImpl(ProgressManager.getInstance(), securityService, requestExecutor, apiRepositoryCoordinates)
    val filesService = GHPRFilesServiceImpl(ProgressManager.getInstance(), requestExecutor, apiRepositoryCoordinates)

    val searchHolder = GHPRSearchQueryHolderImpl().apply {
      query = GHPRSearchQuery.DEFAULT
    }
    val listLoader = GHGQLPagedListLoader(ProgressManager.getInstance(),
                                          SimpleGHGQLPagesLoader(requestExecutor, { p ->
                                            GHGQLRequests.PullRequest.search(account.server,
                                                                             buildQuery(apiRepositoryPath, searchHolder.query),
                                                                             p)
                                          }))
    val listUpdatesChecker = GHPRListETagUpdateChecker(ProgressManager.getInstance(), requestExecutor, account.server, apiRepositoryPath)

    val dataProviderRepository = GHPRDataProviderRepositoryImpl(detailsService, stateService, reviewService, filesService, commentService,
                                                                changesService) { id ->
      GHGQLPagedListLoader(ProgressManager.getInstance(),
                           SimpleGHGQLPagesLoader(requestExecutor, { p ->
                             GHGQLRequests.PullRequest.Timeline.items(account.server, apiRepositoryPath.owner, apiRepositoryPath.repository,
                                                                      id.number, p)
                           }, true))
    }

    val repoDataService = GHPRRepositoryDataServiceImpl(ProgressManager.getInstance(), requestExecutor,
                                                        remoteCoordinates, apiRepositoryCoordinates,
                                                        repoOwner,
                                                        repositoryInfo.id, repositoryInfo.defaultBranch, repositoryInfo.isFork)

    val avatarIconsProvider = GHAvatarIconsProvider(CachingGHUserAvatarLoader.getInstance(), requestExecutor)

    val filesManager = GHPRFilesManagerImpl(project, parsedRepositoryCoordinates)

    indicator.checkCanceled()
    val creationService = GHPRCreationServiceImpl(ProgressManager.getInstance(), requestExecutor, repoDataService)
    return GHPRDataContext(searchHolder, listLoader, listUpdatesChecker, dataProviderRepository,
                           securityService, repoDataService, creationService, detailsService, avatarIconsProvider, filesManager,
                           GHPRDiffRequestModelImpl())
  }

  @RequiresEdt
  fun findContext(repositoryCoordinates: GHRepositoryCoordinates): GHPRDataContext? = repositories[repositoryCoordinates]?.lastLoadedValue

  companion object {
    private val LOG = logger<GHPRDataContextRepository>()

    fun getInstance(project: Project) = project.service<GHPRDataContextRepository>()

    private fun buildQuery(repoPath: GHRepositoryPath, searchQuery: GHPRSearchQuery?): String {
      return GithubApiSearchQueryBuilder.searchQuery {
        qualifier("type", GithubIssueSearchType.pr.name)
        qualifier("repo", repoPath.toString())
        searchQuery?.buildApiSearchQuery(this)
      }
    }
  }
}