// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcs.git.shared.rpc.GitRepositoryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

// This class is temporarily moved to the shared module until the branch widget can be fully moved to the frontend.
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitRepositoriesFrontendHolder(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  private val repositories: MutableMap<RepositoryId, GitRepositoryFrontendModelImpl> = ConcurrentHashMap()
  private val widgetUpdateFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @Volatile
  var initialized: Boolean = false
    private set

  private val initLock = Mutex()

  fun getAll(): List<GitRepositoryFrontendModel> {
    logErrorIfNotInitialized()
    return repositories.values.toList()
  }

  fun get(repositoryId: RepositoryId): GitRepositoryFrontendModel {
    logErrorIfNotInitialized()
    return repositories[repositoryId] ?: error("State of repository $repositoryId is not synchronized")
  }

  /**
   * Caller should ensure that [initialized] is set to true before accessing the data in [GitRepositoriesFrontendHolder]
   */
  private fun logErrorIfNotInitialized() {
    if (!initialized) {
      LOG.error("Repositories state holder not initialized", Throwable())
    }
  }

  suspend fun init() {
    if (initialized) return
    initLock.withLock {
      if (initialized) return

      val syncScope = cs.childScope("Git repository state synchronization")

      GitRepositoryApi.getInstance().getRepositories(project.projectId()).forEach {
        repositories[it.repositoryId] = convertToRepositoryInfo(it)
      }

      syncScope.launch {
        GitRepositoryApi.getInstance().getRepositoriesEvents(project.projectId()).collect { event ->
          LOG.debug("Received repository event: $event")

          when (event) {
            is GitRepositoryEvent.RepositoryCreated -> {
              repositories[event.repository.repositoryId] = convertToRepositoryInfo(event.repository)
            }
            is GitRepositoryEvent.RepositoryDeleted -> {
              repositories.remove(event.repositoryId)
            }
            is GitRepositoryEvent.FavoriteRefsUpdated -> {
              repositories.computeIfPresent(event.repositoryId) { k, info ->
                info.favoriteRefs = event.favoriteRefs
                info
              }
            }
            is GitRepositoryEvent.RepositoryStateUpdated -> {
              repositories.computeIfPresent(event.repositoryId) { k, info ->
                info.state = event.newState
                info
              }
            }
          }

          widgetUpdateFlow.tryEmit(Unit)
        }
      }

      initialized = true
    }
  }

  companion object {
    fun getInstance(project: Project): GitRepositoriesFrontendHolder = project.getService(GitRepositoriesFrontendHolder::class.java)

    private val LOG = Logger.getInstance(GitRepositoriesFrontendHolder::class.java)

    private fun convertToRepositoryInfo(repositoryDto: GitRepositoryDto) =
      GitRepositoryFrontendModelImpl(
        repositoryId = repositoryDto.repositoryId,
        shortName = repositoryDto.shortName,
        state = repositoryDto.state,
        favoriteRefs = repositoryDto.favoriteRefs,
      )
  }
}

private class GitRepositoryFrontendModelImpl(
  override val repositoryId: RepositoryId,
  override val shortName: String,
  override var state: GitRepositoryState,
  override var favoriteRefs: GitFavoriteRefs,
): GitRepositoryFrontendModel