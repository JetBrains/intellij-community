// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
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
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchQueryHolderImpl
import org.jetbrains.plugins.github.util.*
import java.io.IOException
import java.util.concurrent.CompletableFuture

@Service
internal class GHPRDataContextRepository(private val project: Project) {

  private val repositories = mutableMapOf<GitRemoteUrlCoordinates, LazyCancellableBackgroundProcessValue<GHPRDataContext>>()

  @CalledInAwt
  fun acquireContext(gitRemoteCoordinates: GitRemoteUrlCoordinates,
                     account: GithubAccount, requestExecutor: GithubApiRequestExecutor): CompletableFuture<GHPRDataContext> {

    return repositories.getOrPut(gitRemoteCoordinates) {
      val contextDisposable = Disposer.newDisposable()
      LazyCancellableBackgroundProcessValue.create { indicator ->
        ProgressManager.getInstance().submitIOTask(indicator) {
          try {
            loadContext(indicator, account, requestExecutor, gitRemoteCoordinates)
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

  @CalledInAwt
  fun clearContext(gitRemoteCoordinates: GitRemoteUrlCoordinates) {
    repositories.remove(gitRemoteCoordinates)?.drop()
  }

  @CalledInBackground
  @Throws(IOException::class)
  private fun loadContext(indicator: ProgressIndicator,
                          account: GithubAccount, requestExecutor: GithubApiRequestExecutor,
                          gitRemoteCoordinates: GitRemoteUrlCoordinates): GHPRDataContext {
    val fullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(gitRemoteCoordinates.url)
                   ?: throw IllegalArgumentException(
                     "Invalid GitHub Repository URL - ${gitRemoteCoordinates.url} is not a GitHub repository")

    indicator.text = GithubBundle.message("pull.request.loading.account.info")
    val accountDetails = GithubAccountInformationProvider.getInstance().getInformation(requestExecutor, indicator, account)
    indicator.checkCanceled()

    indicator.text = GithubBundle.message("pull.request.loading.repo.info")
    val repoWithPermissions =
      requestExecutor.execute(indicator, GHGQLRequests.Repo.findPermission(GHRepositoryCoordinates(account.server, fullPath)))
      ?: throw IllegalArgumentException("Repository $fullPath does not exist at ${account.server} or you don't have access.")

    val currentUser = GHUser(accountDetails.nodeId, accountDetails.login, accountDetails.htmlUrl, accountDetails.avatarUrl!!,
                             accountDetails.name)

    indicator.text = GithubBundle.message("pull.request.loading.user.teams.info")
    val repoOwner = repoWithPermissions.owner
    val currentUserTeams = if (repoOwner is GHRepositoryOwnerName.Organization)
      SimpleGHGQLPagesLoader(requestExecutor, {
        GHGQLRequests.Organization.Team.findByUserLogins(account.server, repoOwner.login, listOf(currentUser.login), it)
      }).loadAll(indicator)
    else emptyList()
    indicator.checkCanceled()

    val repositoryPath = repoWithPermissions.path
    val repositoryCoordinates = GHRepositoryCoordinates(account.server, repositoryPath)

    val securityService = GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(project),
                                                  account, currentUser, currentUserTeams,
                                                  repoWithPermissions)
    val detailsService = GHPRDetailsServiceImpl(ProgressManager.getInstance(), requestExecutor, repositoryCoordinates)
    val stateService = GHPRStateServiceImpl(ProgressManager.getInstance(), securityService,
                                            requestExecutor, account.server, repositoryPath)
    val commentService = GHPRCommentServiceImpl(ProgressManager.getInstance(), requestExecutor, repositoryCoordinates)
    val changesService = GHPRChangesServiceImpl(ProgressManager.getInstance(), project, requestExecutor,
                                                gitRemoteCoordinates, repositoryCoordinates)
    val reviewService = GHPRReviewServiceImpl(ProgressManager.getInstance(), securityService, requestExecutor, repositoryCoordinates)

    val searchHolder = GHPRSearchQueryHolderImpl().apply {
      query = GHPRSearchQuery.DEFAULT
    }
    val listLoader = GHGQLPagedListLoader(ProgressManager.getInstance(),
                                          SimpleGHGQLPagesLoader(requestExecutor, { p ->
                                            GHGQLRequests.PullRequest.search(account.server,
                                                                             buildQuery(repositoryPath, searchHolder.query),
                                                                             p)
                                          }))
    val listUpdatesChecker = GHPRListETagUpdateChecker(ProgressManager.getInstance(), requestExecutor, account.server, repositoryPath)

    val dataProviderRepository = GHPRDataProviderRepositoryImpl(detailsService, stateService, reviewService, commentService,
                                                                changesService) { id ->
      GHGQLPagedListLoader(ProgressManager.getInstance(),
                           SimpleGHGQLPagesLoader(requestExecutor, { p ->
                             GHGQLRequests.PullRequest.Timeline.items(account.server, repositoryPath.owner, repositoryPath.repository,
                                                                      id.number, p)
                           }, true))
    }

    val repoDataService = GHPRRepositoryDataServiceImpl(ProgressManager.getInstance(), requestExecutor, account.server,
                                                        repositoryPath, repoOwner)

    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(CachingGithubUserAvatarLoader.getInstance(),
                                                                              GithubImageResizer.getInstance(),
                                                                              requestExecutor)

    val filesManager = GHPRFilesManagerImpl(project, repositoryCoordinates)

    indicator.checkCanceled()
    return GHPRDataContext(gitRemoteCoordinates, repositoryCoordinates, searchHolder, listLoader, listUpdatesChecker,
                           dataProviderRepository, securityService, repoDataService, avatarIconsProviderFactory, filesManager)
  }

  @CalledInAwt
  fun findContext(repositoryCoordinates: GHRepositoryCoordinates): GHPRDataContext? =
    repositories.values.mapNotNull { it.lastLoadedValue }.find { it.repositoryCoordinates == repositoryCoordinates }

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