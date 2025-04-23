// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.repo.GitRepositoryState
import com.intellij.vcs.git.shared.rpc.GitReferencesSet
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcs.git.shared.rpc.GitRepositoryEvent
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitDisposable
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.tree.tags
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

class GitRepositoryApiImpl : GitRepositoryApi {
  override suspend fun getRepositories(projectId: ProjectId): List<GitRepositoryDto> {
    val project = projectId.findProject()
    val repositories = getAllRepositories(project)
    if (LOG.isDebugEnabled) {
      LOG.debug("Known repositories: $repositories")
    }
    return repositories.map { convertToDto(it) }
  }

  override suspend fun getRepositoriesEvents(projectId: ProjectId): Flow<GitRepositoryEvent> {
    val project = projectId.findProject()
    val scope = GitDisposable.getInstance(project).coroutineScope.childScope("Git repository synchronizer in ${project}")

    return flowWithMessageBus(project, scope) { connection ->
      val synchronizer = Synchronizer(project, this@flowWithMessageBus)
      getAllRepositories(project).forEach(synchronizer::sendDeletedEventOnDispose)

      connection.subscribe(GitRepositoryFrontendSynchronizer.TOPIC, synchronizer)
    }
  }

  private inner class Synchronizer(
    private val project: Project,
    private val channel: SendChannel<GitRepositoryEvent>,
  ) : GitRepositoryFrontendSynchronizer {
    override fun repositoryCreated(repository: GitRepository) {
      if (repository.isDisposed) return

      sendDeletedEventOnDispose(repository)
      val dto = convertToDto(repository)

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

      val repositoryState = convertRepositoryState(repository)
      channel.trySend(GitRepositoryEvent.RepositoryStateUpdated(repository.rpcId, repositoryState))
    }

    override fun favoriteRefsUpdated(repository: GitRepository?) {
      if (repository?.isDisposed == true) return

      LOG.debug("Updating favorite refs for ${repository?.root ?: "all git repos"}")

      val update = mutableMapOf<RepositoryId, GitFavoriteRefs>()
      if (repository != null) {
        update[repository.rpcId] = collectFavorites(repository)
      }
      else {
        getAllRepositories(project).forEach { repository ->
          update[repository.rpcId] = collectFavorites(repository)
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

    private fun convertToDto(repository: GitRepository): GitRepositoryDto {
      return GitRepositoryDto(
        repositoryId = repository.rpcId,
        shortName = VcsUtil.getShortVcsRootName(repository.project, repository.root),
        state = convertRepositoryState(repository),
        favoriteRefs = collectFavorites(repository)
      )
    }

    private fun convertRepositoryState(repository: GitRepository): GitRepositoryState {
      val refsSet = GitReferencesSet(
        repository.info.localBranchesWithHashes.keys,
        repository.info.remoteBranchesWithHashes.keys.filterIsInstance<GitStandardRemoteBranch>().toSet(),
        repository.tags.keys,
      )
      val currentRef = repository.info.currentBranch?.fullName

      return GitRepositoryState(
        currentRef,
        refsSet,
        repository.branches.recentCheckoutBranches,
      )
    }

    private fun collectFavorites(repository: GitRepository): GitFavoriteRefs {
      val branchManager = repository.project.service<GitBranchManager>()
      return GitFavoriteRefs(
        localBranches = branchManager.getFavoriteRefs(GitBranchType.LOCAL, repository),
        remoteBranches = branchManager.getFavoriteRefs(GitBranchType.REMOTE, repository),
        tags = branchManager.getFavoriteRefs(GitTagType, repository),
      )
    }
  }
}