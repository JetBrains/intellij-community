// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
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
import org.jetbrains.plugins.github.i18n.GithubBundle
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
          val context = withContext(Dispatchers.IO) {
            runUnderIndicator {
              loadContext(account, requestExecutor, repository, remote)
            }
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
    cacheGuard.withLock {
      withContext(cs.coroutineContext) {
        cache.remove(repository)?.let {
          Disposer.dispose(it)
        }
      }
    }
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  private fun loadContext(account: GithubAccount,
                          requestExecutor: GithubApiRequestExecutor,
                          parsedRepositoryCoordinates: GHRepositoryCoordinates,
                          remoteCoordinates: GitRemoteUrlCoordinates): GHPRDataContext {
    val indicator: ProgressIndicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
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

    val iconsScope = MainScope()
    val avatarIconsProvider = CachingIconsProvider(AsyncImageIconsProvider(iconsScope, ImageLoader(requestExecutor)))

    val filesManager = GHPRFilesManagerImpl(project, parsedRepositoryCoordinates)

    indicator.checkCanceled()
    val creationService = GHPRCreationServiceImpl(ProgressManager.getInstance(), requestExecutor, repoDataService)
    return GHPRDataContext(listLoader, listUpdatesChecker, dataProviderRepository,
                           securityService, repoDataService, creationService, detailsService, avatarIconsProvider, filesManager,
                           GHPRDiffRequestModelImpl()).also {
      Disposer.register(it, Disposable { iconsScope.cancel() })
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

  override fun dispose() {
    runBlocking { cacheGuard.lock() }
    try {
      val toDispose = cache.values.toList()
      cache.clear()
      toDispose.forEach {
        Disposer.dispose(it)
      }
    }
    finally {
      runBlocking { cacheGuard.unlock() }
    }
  }

  companion object {
    private val LOG = logger<GHPRDataContextRepository>()

    fun getInstance(project: Project) = project.service<GHPRDataContextRepository>()
  }
}