// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.ref.GitReferenceName
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcs.git.shared.rpc.GitRepositoryEvent
import git4idea.GitDisposable
import git4idea.branch.GitRefType
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

private typealias SharedRefUtil = com.intellij.vcs.git.shared.ref.GitRefUtil

class GitRepositoryApiImpl : GitRepositoryApi {
  override suspend fun getRepositories(projectId: ProjectId): List<GitRepositoryDto> {
    val project = projectId.findProject()
    val repositories = getAllRepositories(project)
    if (LOG.isDebugEnabled) {
      LOG.debug("Known repositories: $repositories")
    }
    return repositories.map { GitRepositoryToDtoConverter.convertToDto(it) }
  }

  override suspend fun getRepositoriesEvents(projectId: ProjectId): Flow<GitRepositoryEvent> {
    val project = projectId.findProject()
    val scope = GitDisposable.getInstance(project).childScope("Git repository synchronizer in ${project}")

    return flowWithMessageBus(project, scope) { connection ->
      val synchronizer = Synchronizer(project, this@flowWithMessageBus)
      getAllRepositories(project).forEach(synchronizer::sendDeletedEventOnDispose)

      connection.subscribe(GitRepositoryFrontendSynchronizer.TOPIC, synchronizer)
    }
  }

  override suspend fun toggleFavorite(projectId: ProjectId, repositories: List<RepositoryId>, reference: GitReferenceName, favorite: Boolean) {
    val project = projectId.findProject()

    val resolvedRepositories = resolveRepositories(project, repositories)

    val refType = GitRefType.of(reference)
    val branchManager = project.service<GitBranchManager>()
    resolvedRepositories.forEach {
      branchManager.setFavorite(refType,
                                it,
                                SharedRefUtil.stripRefsPrefix(reference.fullName),
                                favorite)
    }
  }

  private inner class Synchronizer(
    private val project: Project,
    private val channel: SendChannel<GitRepositoryEvent>,
  ) : GitRepositoryFrontendSynchronizer {
    override fun repositoryCreated(repository: GitRepository) {
      if (repository.isDisposed) return

      sendDeletedEventOnDispose(repository)
      val dto = GitRepositoryToDtoConverter.convertToDto(repository)

      if (LOG.isDebugEnabled) {
        val refsSet = dto.state.refs
        val refsString = "${refsSet.localBranches.size} local branches, " +
                         "${refsSet.remoteBranches.size} remote branches, " +
                         "${refsSet.tags.size} tags"
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

      LOG.debug("Tags were loaded for ${repository.root}. Updating state")

      val repositoryState = GitRepositoryToDtoConverter.convertRepositoryState(repository)
      channel.trySend(GitRepositoryEvent.TagsLoaded(repository.rpcId, repositoryState))
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
  }

  private companion object {
    val LOG = Logger.getInstance(GitRepositoryApiImpl::class.java)

    private fun getAllRepositories(project: Project): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    private fun resolveRepositories(project: Project, repositoryIds: List<RepositoryId>): List<GitRepository> {
      val repositories = GitRepositoryManager.getInstance(project).repositories.associateBy { it.rpcId }

      val notFound = mutableListOf<RepositoryId>()
      val resolved = repositoryIds.mapNotNull {
        val resolved = repositories[it]
        if (resolved == null) notFound.add(it)
        resolved
      }

      assert(notFound.isEmpty()) { "Not found repositories: $notFound" }

      return resolved
    }
  }
}