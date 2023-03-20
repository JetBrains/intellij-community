// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.IconUtil
import com.intellij.util.childScope
import com.intellij.util.ui.ImageUtil
import git4idea.remote.GitRemoteUrlCoordinates
import icons.CollaborationToolsIcons
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.pullrequest.GHPRDiffRequestModelImpl
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import java.awt.Image
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@Service
internal class GHPRDataContextRepository(private val project: Project) : Disposable {

  private val cs = disposingScope()

  private val cache = ConcurrentHashMap<GHRepositoryCoordinates, GHPRDataContext>()
  private val cacheGuard = Mutex()

  suspend fun getContext(repository: GHRepositoryCoordinates, remote: GitRemoteUrlCoordinates,
                         account: GithubAccount, requestExecutor: GithubApiRequestExecutor): GHPRDataContext =
    withContext(cs.coroutineContext) {
      cacheGuard.withLock {
        val existing = cache[repository]
        if (existing != null) return@withLock existing
        try {
          val contextScope = cs.childScope()
          val context = withContext(contextScope.coroutineContext) {
            loadContext(contextScope, account, requestExecutor, repository, remote)
          }
          cache[repository] = context
          context
        }
        catch (e: Exception) {
          if (e !is CancellationException) LOG.info("Error occurred while creating data context", e)
          throw e
        }
      }
    }

  suspend fun clearContext(repository: GHRepositoryCoordinates) {
    withContext(cs.coroutineContext) {
      cacheGuard.withLock {
        cache.remove(repository)?.scope?.coroutineContext?.get(Job)?.cancelAndJoin()
      }
    }
  }

  @Throws(IOException::class)
  private suspend fun loadContext(contextScope: CoroutineScope,
                                  account: GithubAccount,
                                  requestExecutor: GithubApiRequestExecutor,
                                  parsedRepositoryCoordinates: GHRepositoryCoordinates,
                                  remoteCoordinates: GitRemoteUrlCoordinates): GHPRDataContext {
    val accountDetails = suspendingApiCall { indicator ->
      GithubAccountInformationProvider.getInstance().getInformation(requestExecutor, indicator, account)
    }

    val ghostUserDetails = suspendingApiCall { indicator ->
      requestExecutor.execute(indicator, GHGQLRequests.User.find(account.server, "ghost"))
      ?: error("Couldn't load ghost user details")
    }


    val repositoryInfo = suspendingApiCall { indicator ->
      requestExecutor.execute(indicator,
                              GHGQLRequests.Repo.find(GHRepositoryCoordinates(account.server, parsedRepositoryCoordinates.repositoryPath)))
      ?: throw IllegalArgumentException(
        "Repository ${parsedRepositoryCoordinates.repositoryPath} does not exist at ${account.server} or you don't have access.")

    }

    val currentUser = GHUser(accountDetails.nodeId, accountDetails.login, accountDetails.htmlUrl, accountDetails.avatarUrl!!,
                             accountDetails.name)


    val repoOwner = repositoryInfo.owner
    val currentUserTeams = if (repoOwner is GHRepositoryOwnerName.Organization) {
      suspendingApiCall { indicator ->
        SimpleGHGQLPagesLoader(requestExecutor, {
          GHGQLRequests.Organization.Team.findByUserLogins(account.server, repoOwner.login, listOf(currentUser.login), it)
        }).loadAll(indicator)
      }
    }
    else {
      emptyList()
    }

    // repository might have been renamed/moved
    val apiRepositoryPath = repositoryInfo.path
    val apiRepositoryCoordinates = GHRepositoryCoordinates(account.server, apiRepositoryPath)

    val securityService = GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(project),
                                                  ghostUserDetails,
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

    val listLoader = GHPRListLoader(ProgressManager.getInstance(), requestExecutor, apiRepositoryCoordinates)
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

    val iconsScope = contextScope.childScope(Dispatchers.Main)
    val avatarIconsProvider = CachingIconsProvider(AsyncImageIconsProvider(iconsScope, ImageLoader(requestExecutor)))

    val filesManager = GHPRFilesManagerImpl(project, parsedRepositoryCoordinates)

    val creationService = GHPRCreationServiceImpl(ProgressManager.getInstance(), requestExecutor, repoDataService)
    return GHPRDataContext(contextScope, listLoader, listUpdatesChecker, dataProviderRepository,
                           securityService, repoDataService, creationService, detailsService, avatarIconsProvider, filesManager,
                           GHPRDiffRequestModelImpl()).also {
      Disposer.register(this, changesService)
    }
  }

  private class ImageLoader(private val requestExecutor: GithubApiRequestExecutor)
    : AsyncImageIconsProvider.AsyncImageLoader<String> {

    private val avatarsLoader = CachingGHUserAvatarLoader.getInstance()

    override suspend fun load(key: String): Image? =
      avatarsLoader.requestAvatar(requestExecutor, key).await()

    override fun createBaseIcon(key: String?, iconSize: Int): Icon =
      IconUtil.resizeSquared(CollaborationToolsIcons.Review.DefaultAvatar, iconSize)

    override suspend fun postProcess(image: Image): Image =
      ImageUtil.createCircleImage(ImageUtil.toBufferedImage(image))
  }

  // dangerous to do this without lock, but making it suspendable is too much work
  fun findContext(repositoryCoordinates: GHRepositoryCoordinates): GHPRDataContext? = cache[repositoryCoordinates]

  override fun dispose() = Unit

  companion object {
    private val LOG = logger<GHPRDataContextRepository>()

    fun getInstance(project: Project) = project.service<GHPRDataContextRepository>()

    private suspend fun <T> suspendingApiCall(call: (ProgressIndicator) -> T): T =
      withContext(Dispatchers.IO) {
        coroutineToIndicator {
          val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
          call(indicator)
        }
      }
  }
}