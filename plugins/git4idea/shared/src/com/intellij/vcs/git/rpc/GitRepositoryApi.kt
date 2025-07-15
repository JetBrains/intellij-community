// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.ref.GitCurrentRef
import com.intellij.vcs.git.ref.GitFavoriteRefs
import com.intellij.vcs.git.ref.GitReferenceName
import com.intellij.vcs.git.repo.GitHash
import com.intellij.vcs.git.repo.GitOperationState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import git4idea.GitDisposable
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitRepositoryApi : RemoteApi<Unit> {
  /**
   * Subscription to repositories' updates in the given project.
   * [GitRepositoryEvent] is sent produced on corresponding updates.
   * However, [GitRepositoryEvent.RepositoriesSync] is sent periodically and once the connection is established
   */
  suspend fun getRepositoriesEvents(projectId: ProjectId): Flow<GitRepositoryEvent>

  /**
   * Forces synchronization of all repository states.
   * State is sent in [getRepositoriesEvents] flow as [GitRepositoryEvent.ReloadState]
   */
  suspend fun forceSync(projectId: ProjectId)

  suspend fun toggleFavorite(projectId: ProjectId, repositories: List<RepositoryId>, reference: GitReferenceName, favorite: Boolean)

  companion object {
    suspend fun getInstance(): GitRepositoryApi = RemoteApiProviderService.resolve(remoteApiDescriptor<GitRepositoryApi>())

    fun launchRequest(project: Project, request: suspend GitRepositoryApi.() -> Unit) {
      GitDisposable.getInstance(project).coroutineScope.launch {
        getInstance().request()
      }
    }
  }
}

@Serializable
@ApiStatus.Internal
sealed interface GitRepositoryEvent {
  sealed interface SingleRepositoryUpdate: GitRepositoryEvent {
    val repositoryId: RepositoryId
  }

  @Serializable
  @ApiStatus.Internal
  class ReloadState(val repositories: List<GitRepositoryDto>) : GitRepositoryEvent {
    override fun toString(): String = "Full state of ${repositories.size} repositories"
  }

  @Serializable
  @ApiStatus.Internal
  class RepositoriesSync(val repositories: List<RepositoryId>) : GitRepositoryEvent {
    override fun toString(): String = "Sync of ${repositories.size} repositories"
  }

  @Serializable
  @ApiStatus.Internal
  class RepositoryCreated(val repository: GitRepositoryDto) : GitRepositoryEvent {
    override fun toString(): String = "Repository created ${repository.repositoryId}"
  }

  @Serializable
  @ApiStatus.Internal
  class RepositoryStateUpdated(
    override val repositoryId: RepositoryId,
    val newState: GitRepositoryStateDto,
  ) : SingleRepositoryUpdate {
    override fun toString(): String = "Repository updated ${repositoryId}"
  }

  @Serializable
  @ApiStatus.Internal
  class TagsLoaded(override val repositoryId: RepositoryId, val tags: Set<GitTag>) : SingleRepositoryUpdate {
    override fun toString(): String = "Tags loaded in ${repositoryId}"
  }

  @Serializable
  @ApiStatus.Internal
  data object TagsHidden : GitRepositoryEvent {
    override fun toString(): String = "Tags hidden"
  }

  @Serializable
  @ApiStatus.Internal
  class FavoriteRefsUpdated(
    override val repositoryId: RepositoryId,
    val favoriteRefs: GitFavoriteRefs,
  ) : SingleRepositoryUpdate {
    override fun toString(): String = "Favorite refs updated ${repositoryId}"
  }

  @Serializable
  @ApiStatus.Internal
  class RepositoryDeleted(val repositoryId: RepositoryId) : GitRepositoryEvent {
    override fun toString(): String = "Repository deleted ${repositoryId}"
  }
}

@Serializable
@ApiStatus.Internal
class GitRepositoryDto(
  val repositoryId: RepositoryId,
  val shortName: String,
  val state: GitRepositoryStateDto,
  val favoriteRefs: GitFavoriteRefs,
  val root: VirtualFileId,
)

@Serializable
@ApiStatus.Internal
class GitRepositoryStateDto(
  val currentRef: GitCurrentRef?,
  val revision: GitHash?,
  val localBranches: Set<GitStandardLocalBranch>,
  val remoteBranches: Set<GitStandardRemoteBranch>,
  val tags: Set<GitTag>,
  val recentBranches: List<GitStandardLocalBranch>,
  val operationState: GitOperationState,
  /**
   * Maps short names of local branches to their upstream branches.
   */
  val trackingInfo: Map<String, GitStandardRemoteBranch>,
)