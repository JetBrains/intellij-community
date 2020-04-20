// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import git4idea.commands.Git
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext.Companion.PULL_REQUEST_EDITED_TOPIC
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext.Companion.PullRequestEditedListener
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolderImpl
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import org.jetbrains.plugins.github.util.GithubUrlUtil
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
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
      LazyCancellableBackgroundProcessValue.create(ProgressManager.getInstance()) { indicator ->
        loadContext(indicator, account, requestExecutor, gitRemoteCoordinates).also { ctx ->
          invokeAndWaitIfNeeded {
            if (Disposer.isDisposed(contextDisposable)) {
              Disposer.dispose(ctx)
            }
            else {
              Disposer.register(contextDisposable, ctx)
              Disposer.register(ctx, Disposable {
                val editorManager = FileEditorManager.getInstance(project)
                editorManager.openFiles.filter { it is GHPRVirtualFile && it.dataContext === ctx }.forEach(editorManager::closeFile)
              })
            }
          }
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

    val repositoryCoordinates = GHRepositoryCoordinates(account.server, repoWithPermissions.path)

    val messageBus = MessageBusFactory.getInstance().createMessageBus(object : MessageBusOwner {
      override fun isDisposed() = project.isDisposed

      override fun createListener(descriptor: ListenerDescriptor) = throw UnsupportedOperationException()
    })

    val securityService = GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(project), currentUser, currentUserTeams,
                                                  repoWithPermissions)
    val detailsService = GHPRDetailsServiceImpl(ProgressManager.getInstance(), requestExecutor, repositoryCoordinates)
    val reviewService = GHPRReviewServiceImpl(ProgressManager.getInstance(), messageBus, securityService, requestExecutor,
                                              repositoryCoordinates)
    val commentService = GHPRCommentServiceImpl(ProgressManager.getInstance(), messageBus, requestExecutor, repositoryCoordinates)

    val listModel = CollectionListModel<GHPullRequestShort>()
    val searchHolder = GithubPullRequestSearchQueryHolderImpl()
    val listLoader = GHPRListLoaderImpl(ProgressManager.getInstance(), requestExecutor, account.server, repoWithPermissions.path, listModel,
                                        searchHolder)

    val dataLoader = GHPRDataLoaderImpl { id ->
      val detailsData = GHPRDetailsDataProviderImpl(detailsService, id).apply {
        addDetailsLoadedListener(this) {
          loadedDetails?.let { listLoader.updateData(it) }
        }
      }
      val reviewData = GHPRReviewDataProviderImpl(reviewService, id)
      val commentsData = GHPRCommentsDataProviderImpl(commentService, id)
      GHPRDataProviderImpl(project, ProgressManager.getInstance(), Git.getInstance(), securityService, requestExecutor,
                           gitRemoteCoordinates, repositoryCoordinates, id,
                           detailsData,
                           commentsData,
                           reviewData).also {
        Disposer.register(it, detailsData)
        Disposer.register(it, reviewData)
      }
    }
    messageBus.connect().subscribe(PULL_REQUEST_EDITED_TOPIC, object : PullRequestEditedListener {
      override fun onPullRequestEdited(id: GHPRIdentifier) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(id)
          dataProvider?.detailsData?.reloadDetails()
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }

      override fun onPullRequestReviewsEdited(id: GHPRIdentifier) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(id)
          dataProvider?.reviewData?.resetReviewThreads()
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }

      override fun onPullRequestCommentsEdited(id: GHPRIdentifier) {
        runInEdt {
          val dataProvider = dataLoader.findDataProvider(id)
          dataProvider?.timelineLoader?.loadMore(true)
        }
      }
    })
    val metadataService = GHPRMetadataServiceImpl(ProgressManager.getInstance(), messageBus, requestExecutor, account.server,
                                                  repoWithPermissions.path, repoOwner)
    val stateService = GHPRStateServiceImpl(ProgressManager.getInstance(), messageBus,
                                            requestExecutor, account.server, repoWithPermissions.path)

    return GHPRDataContext(gitRemoteCoordinates, repositoryCoordinates, account,
                           requestExecutor, messageBus, listModel, searchHolder, listLoader, dataLoader, securityService,
                           metadataService, stateService, reviewService, commentService)
  }

  companion object {
    fun getInstance(project: Project) = project.service<GHPRDataContextRepository>()
  }
}