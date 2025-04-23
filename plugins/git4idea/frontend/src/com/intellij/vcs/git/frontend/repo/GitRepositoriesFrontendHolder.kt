// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.repo

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.frontend.widget.GitWidgetUpdateListener
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcs.git.shared.rpc.GitRepositoryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class GitRepositoriesFrontendHolder(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  private val repositories: MutableMap<RepositoryId, GitRepositoryFrontendModel> = ConcurrentHashMap()
  private val widgetUpdateFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun getAll(): List<GitRepositoryFrontendModel> = repositories.values.toList()

  suspend fun init() {
    val syncScope = cs.childScope("Git repository state synchronization")

    GitRepositoryApi.getInstance().getRepositories(project.projectId()).forEach {
      repositories[it.repositoryId] = convertToRepositoryInfo(it)
    }

    syncScope.launch {
      @OptIn(FlowPreview::class)
      widgetUpdateFlow.debounce(100.milliseconds).collect {
        project.messageBus.syncPublisher(GitWidgetUpdateListener.TOPIC).triggerUpdate()
      }
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
  }

  companion object {
    fun getInstance(project: Project): GitRepositoriesFrontendHolder = project.getService(GitRepositoriesFrontendHolder::class.java)

    private val LOG = Logger.getInstance(GitRepositoriesFrontendHolder::class.java)

    private fun convertToRepositoryInfo(repositoryDto: GitRepositoryDto): GitRepositoryFrontendModel =
      GitRepositoryFrontendModel(
        repositoryId = repositoryDto.repositoryId,
        shortName = repositoryDto.shortName,
        state = repositoryDto.state,
        favoriteRefs = repositoryDto.favoriteRefs,
      )
  }
}