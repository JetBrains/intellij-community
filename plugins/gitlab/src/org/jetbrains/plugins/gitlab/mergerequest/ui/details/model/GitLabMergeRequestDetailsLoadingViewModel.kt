// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel.LoadingState

private val LOG = logger<GitLabMergeRequestDetailsLoadingViewModel>()

internal interface GitLabMergeRequestDetailsLoadingViewModel {
  val mergeRequestId: String
  val mergeRequestLoadingFlow: Flow<LoadingState>

  fun requestLoad()

  fun refreshData()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val detailsVm: GitLabMergeRequestDetailsViewModelImpl) : LoadingState
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestDetailsLoadingViewModelImpl(
  project: Project,
  parentScope: CoroutineScope,
  currentUser: GitLabUserDTO,
  private val projectData: GitLabProject,
  override val mergeRequestId: String
) : GitLabMergeRequestDetailsLoadingViewModel {
  private val scope = parentScope.childScope(Dispatchers.Default)

  private val mrStore = projectData.mergeRequests

  private val loadingRequests = MutableSharedFlow<Unit>(1)

  private val mergeRequestFlow: Flow<Result<GitLabMergeRequest>> = loadingRequests.flatMapLatest {
    mrStore.getShared(mergeRequestId)
  }.modelFlow(scope, LOG)

  override val mergeRequestLoadingFlow: Flow<LoadingState> = channelFlow {
    mergeRequestFlow.collectLatest { mrResult ->
      send(LoadingState.Loading)
      coroutineScope {
        val result = try {
          val detailsVm = GitLabMergeRequestDetailsViewModelImpl(project, scope, currentUser, projectData, mrResult.getOrThrow())
          LoadingState.Result(detailsVm)
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Exception) {
          LoadingState.Error(e)
        }
        send(result)
        awaitCancellation()
      }
    }
  }.modelFlow(scope, LOG)

  override fun requestLoad() {
    scope.launch {
      loadingRequests.emit(Unit)
    }
  }

  override fun refreshData() {
    scope.launch {
      val mergeRequest = mergeRequestFlow.first().getOrNull()
      mergeRequest?.refreshData()
    }
  }
}