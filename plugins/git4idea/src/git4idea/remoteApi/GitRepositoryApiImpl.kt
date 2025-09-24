// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.ref.GitFavoriteRefs
import com.intellij.vcs.git.ref.GitRefUtil
import com.intellij.vcs.git.ref.GitReferenceName
import com.intellij.vcs.git.rpc.GitRepositoryApi
import com.intellij.vcs.git.rpc.GitRepositoryEvent
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import git4idea.branch.GitRefType
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryIdCache
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private typealias SharedRefUtil = GitRefUtil

class GitRepositoryApiImpl : GitRepositoryApi {
  override suspend fun getRepositoriesEvents(projectId: ProjectId): Flow<GitRepositoryEvent> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      val synchronizer = Synchronizer(project, this)
      // Sending of the initial state should be delayed until initialization is complete
      ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
        launch {
          val allRepositories = getAllRepositories(project)
          allRepositories.forEach { repository -> synchronizer.sendDeletedEventOnDispose(repository) }
          send(GitRepositoryEvent.ReloadState(allRepositories.map { GitRepositoryToDtoConverter.convertToDto(it) }))
          while (isActive) {
            delay(SYNC_INTERVAL)
            send(GitRepositoryEvent.RepositoriesSync(getAllRepositories(project).map { it.rpcId }))
          }
        }
      }

      messageBusConnection.subscribe(GitRepositoryFrontendSynchronizer.TOPIC, synchronizer)
    }

  override suspend fun forceSync(projectId: ProjectId): Unit = projectScoped(projectId) { project ->
    requireOwner()
    project.messageBus.syncPublisher(GitRepositoryFrontendSynchronizer.TOPIC).forceSync()
  }

  override suspend fun toggleFavorite(
    projectId: ProjectId,
    repositories: List<RepositoryId>,
    reference: GitReferenceName,
    favorite: Boolean,
  ): Unit = projectScoped(projectId) { project ->
    requireOwner()

    val resolvedRepositories = GitRepositoryIdCache.getInstance(project).resolveAll(repositories)

    val refType = GitRefType.of(reference)
    val branchManager = project.service<GitBranchManager>()
    resolvedRepositories.forEach {
      branchManager.setFavorite(refType,
                                it,
                                SharedRefUtil.stripRefsPrefix(reference.fullName),
                                favorite)
    }
  }

  private class Synchronizer(
    private val project: Project,
    private val channel: SendChannel<GitRepositoryEvent>,
  ) : GitRepositoryFrontendSynchronizer {
    override fun repositoryCreated(repository: GitRepository) {
      if (repository.isDisposed) return

      sendDeletedEventOnDispose(repository)
      val dto = GitRepositoryToDtoConverter.convertToDto(repository)

      if (LOG.isDebugEnabled) {
        val refsString = "${dto.state.localBranches.size} local branches, " +
                         "${dto.state.remoteBranches.size} remote branches, " +
                         "${dto.state.tags.size} tags"
        LOG.debug("Repository entity created for ${repository.root}\n" +
                  "Current ref: ${dto.state.currentRef}\n" +
                  "Refs: $refsString\n" +
                  "Favorite refs: ${dto.favoriteRefs.size}")
      }

      channel.trySend(GitRepositoryEvent.RepositoryCreated(dto))
    }

    override fun repositoryUpdated(repository: GitRepository) {
      if (repository.isDisposed) return

      LOG.debug("Updating state for ${repository.root}")

      val repositoryState = GitRepositoryToDtoConverter.convertRepositoryState(repository)
      channel.trySend(GitRepositoryEvent.RepositoryStateUpdated(repository.rpcId, repositoryState))
    }

    override fun tagsLoaded(repository: GitRepository) {
      if (repository.isDisposed) return

      // Even though getInfo is annotated with @NotNull, a value can still be missing during the initialization stage.
      // At the same time, tags can be loaded at any point
      @Suppress("SENSELESS_COMPARISON")
      if (repository.info == null) {
        LOG.debug("Tags were loaded while repo is not fully initialized. Skip")
        return
      }

      LOG.debug("Tags were loaded for ${repository.root}. Updating tags state")

      val tagsState = repository.tagHolder.getTags().keys
      channel.trySend(GitRepositoryEvent.TagsLoaded(repository.rpcId, tagsState))
    }

    override fun tagsHidden() {
      LOG.debug("Tags were hidden")
      channel.trySend(GitRepositoryEvent.TagsHidden)
    }

    override fun favoriteRefsUpdated(repository: GitRepository?) {
      if (repository?.isDisposed == true) return

      LOG.debug("Updating favorite refs for ${repository?.root ?: "all git repos"}")

      val update = mutableMapOf<RepositoryId, GitFavoriteRefs>()
      if (repository != null) {
        update[repository.rpcId] = GitRepositoryToDtoConverter.collectFavorites(repository)
      }
      else {
        getAllRepositories(project).forEach { repository ->
          update[repository.rpcId] = GitRepositoryToDtoConverter.collectFavorites(repository)
        }
      }

      update.forEach { id, favoriteRefs ->
        channel.trySend(GitRepositoryEvent.FavoriteRefsUpdated(id, favoriteRefs))
      }
    }

    fun sendDeletedEventOnDispose(repository: GitRepository) {
      Disposer.register(repository, {
        LOG.debug("Notifying repository disposed: $repository")
        channel.trySend(GitRepositoryEvent.RepositoryDeleted(repository.rpcId))
      })
    }

    override fun forceSync() {
      LOG.debug("Synchronization forced")
      val state = getAllRepositories(project).map { GitRepositoryToDtoConverter.convertToDto(it) }
      channel.trySend(GitRepositoryEvent.ReloadState(state))
    }
  }

  private companion object {
    val SYNC_INTERVAL = 10.seconds

    val LOG = Logger.getInstance(GitRepositoryApiImpl::class.java)

    private fun getAllRepositories(project: Project): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories
  }
}