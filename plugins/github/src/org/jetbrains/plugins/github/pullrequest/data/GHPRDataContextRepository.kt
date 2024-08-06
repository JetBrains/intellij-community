// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageToolkit
import git4idea.remote.GitRemoteUrlCoordinates
import icons.CollaborationToolsIcons
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import java.awt.Image
import java.awt.Toolkit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@Service(Service.Level.PROJECT)
internal class GHPRDataContextRepository(private val project: Project, parentCs: CoroutineScope) {

  private val cs = parentCs.childScope()

  private val cache = ConcurrentHashMap<GHRepositoryCoordinates, GHPRDataContext>()
  private val cacheGuard = Mutex()

  suspend fun getContext(repository: GHRepositoryCoordinates, remote: GitRemoteUrlCoordinates,
                         account: GithubAccount, requestExecutor: GithubApiRequestExecutor): GHPRDataContext =
    withContext(cs.coroutineContext) {
      cacheGuard.withLock {
        val existing = cache[repository]
        if (existing != null) return@withLock existing
        try {
          val contextScope = cs.childScope(GHPRDataContext::class.java.name)
          val context = contextScope.getContextAsync(account, requestExecutor, repository, remote).await()
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
  private fun CoroutineScope.getContextAsync(account: GithubAccount,
                                             requestExecutor: GithubApiRequestExecutor,
                                             parsedRepositoryCoordinates: GHRepositoryCoordinates,
                                             remoteCoordinates: GitRemoteUrlCoordinates)
    : Deferred<GHPRDataContext> {
    val cs = this
    return async {
      val accountDetails = GHCachingAccountInformationProvider.getInstance().loadInformation(requestExecutor, account)
      val ghostUserDetails = requestExecutor.executeSuspend(GHGQLRequests.User.find(account.server, "ghost"))
                             ?: error("Couldn't load ghost user details")

      val repositoryInfo =
        requestExecutor.executeSuspend(
          GHGQLRequests.Repo.find(GHRepositoryCoordinates(account.server, parsedRepositoryCoordinates.repositoryPath))
        )
        ?: throw IllegalArgumentException(
          "Repository ${parsedRepositoryCoordinates.repositoryPath} does not exist at ${account.server} or you don't have access.")

      val currentUser = GHUser(accountDetails.nodeId, accountDetails.login, accountDetails.htmlUrl, accountDetails.avatarUrl!!,
                               accountDetails.name)

      // Image loaders
      val iconsScope = cs.childScope(Dispatchers.Main)
      val imageLoader = AsyncHtmlImageLoader { _, src ->
        withContext(cs.coroutineContext + IMAGES_DISPATCHER) {
          val bytes = requestExecutor.executeSuspend(GithubApiRequests.getBytes(src))
          JBImageToolkit.createImage(bytes)
        }
      }
      val avatarIconsProvider = CachingIconsProvider(AsyncImageIconsProvider(iconsScope, AvatarLoader(requestExecutor)))
      val reactionImagesLoader = GHReactionImageLoader(cs, account.server, requestExecutor)
      val reactionIconsProvider = CachingIconsProvider(AsyncImageIconsProvider(iconsScope, reactionImagesLoader))

      // repository might have been renamed/moved
      val apiRepositoryPath = repositoryInfo.path
      val apiRepositoryCoordinates = GHRepositoryCoordinates(account.server, apiRepositoryPath)

      val repoDataService = GHPRRepositoryDataServiceImpl(cs,
                                                          requestExecutor,
                                                          remoteCoordinates, apiRepositoryCoordinates,
                                                          repositoryInfo.owner,
                                                          repositoryInfo.id, repositoryInfo.defaultBranch, repositoryInfo.isFork)
      val securityService = GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(project),
                                                    ghostUserDetails,
                                                    account, currentUser,
                                                    repositoryInfo)
      val detailsService = GHPRDetailsServiceImpl(ProgressManager.getInstance(), project, securityService,
                                                  requestExecutor, apiRepositoryCoordinates)
      val commentService = GHPRCommentServiceImpl(requestExecutor, apiRepositoryCoordinates)
      val changesService = GHPRChangesServiceImpl(cs, project, requestExecutor,
                                                  remoteCoordinates, apiRepositoryCoordinates)
      val reviewService = GHPRReviewServiceImpl(securityService, requestExecutor, apiRepositoryCoordinates)
      val filesService = GHPRFilesServiceImpl(requestExecutor, apiRepositoryCoordinates)
      val reactionsService = GHReactionsServiceImpl(requestExecutor, apiRepositoryCoordinates)

      val listLoader = GHPRListLoader(ProgressManager.getInstance(), requestExecutor, apiRepositoryCoordinates)
      val listUpdatesChecker = GHPRListETagUpdateChecker(ProgressManager.getInstance(), requestExecutor, account.server, apiRepositoryPath)

      val dataProviderRepository = GHPRDataProviderRepositoryImpl(cs,
                                                                  repoDataService,
                                                                  detailsService,
                                                                  reviewService,
                                                                  filesService,
                                                                  commentService,
                                                                  changesService) { id ->
        GHGQLPagedListLoader(ProgressManager.getInstance(),
                             SimpleGHGQLPagesLoader(requestExecutor, { p ->
                               GHGQLRequests.PullRequest.Timeline.items(account.server, apiRepositoryPath.owner,
                                                                        apiRepositoryPath.repository,
                                                                        id.number, p)
                             }, true))
      }

      val filesManager = GHPRFilesManagerImpl(project, apiRepositoryCoordinates)
      val interactionState = project.service<GHPRPersistentInteractionState>()

      val creationService = GHPRCreationServiceImpl(requestExecutor, repoDataService)
      ensureActive()
      GHPRDataContext(cs, listLoader, listUpdatesChecker, dataProviderRepository,
                      securityService, repoDataService, creationService, detailsService, changesService, reactionsService,
                      imageLoader, avatarIconsProvider, reactionIconsProvider,
                      filesManager, interactionState)
    }
  }

  private class AvatarLoader(private val requestExecutor: GithubApiRequestExecutor)
    : AsyncImageIconsProvider.AsyncImageLoader<String> {

    private val avatarsLoader = CachingGHUserAvatarLoader.getInstance()

    override suspend fun load(key: String): Image? =
      avatarsLoader.loadAvatar(requestExecutor, key)

    override fun createBaseIcon(key: String?, iconSize: Int): Icon =
      IconUtil.resizeSquared(CollaborationToolsIcons.Review.DefaultAvatar, iconSize)

    override suspend fun postProcess(image: Image): Image =
      ImageUtil.createCircleImage(ImageUtil.toBufferedImage(image))
  }

  // dangerous to do this without lock, but making it suspendable is too much work
  fun findContext(repositoryCoordinates: GHRepositoryCoordinates): GHPRDataContext? = cache[repositoryCoordinates]

  companion object {
    private val LOG = logger<GHPRDataContextRepository>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val IMAGES_DISPATCHER: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    fun getInstance(project: Project) = project.service<GHPRDataContextRepository>()
  }
}