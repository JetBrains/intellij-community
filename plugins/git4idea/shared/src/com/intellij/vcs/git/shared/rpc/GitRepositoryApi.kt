// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.ref.GitReferenceName
import com.intellij.vcs.git.shared.repo.GitRepositoryState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import git4idea.GitDisposable
import git4idea.GitTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitRepositoryApi : RemoteApi<Unit> {
  suspend fun getRepositories(projectId: ProjectId): List<GitRepositoryDto>

  suspend fun getRepository(repositoryId: RepositoryId): GitRepositoryDto?

  suspend fun getRepositoriesEvents(projectId: ProjectId): Flow<GitRepositoryEvent>

  suspend fun toggleFavorite(projectId: ProjectId, repositories: List<RepositoryId>, reference: GitReferenceName, favorite: Boolean)

  companion object {
    suspend fun getInstance(): GitRepositoryApi = RemoteApiProviderService.resolve(remoteApiDescriptor<GitRepositoryApi>())

    fun launchRequest(project: Project, request: suspend GitRepositoryApi.() -> Unit) {
      GitDisposable.getInstance(project).childScope("GitRepositoryApi call").launch {
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
  class RepositoryCreated(val repository: GitRepositoryDto) : GitRepositoryEvent {
    override fun toString(): String = "Repository created ${repository.repositoryId}"
  }

  @Serializable
  @ApiStatus.Internal
  class RepositoryStateUpdated(
    override val repositoryId: RepositoryId,
    val newState: GitRepositoryState,
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
  val state: GitRepositoryState,
  val favoriteRefs: GitFavoriteRefs,
  val root: VirtualFileId,
)