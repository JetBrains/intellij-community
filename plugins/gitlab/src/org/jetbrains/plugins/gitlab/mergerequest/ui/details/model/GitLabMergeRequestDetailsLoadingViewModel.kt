// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.LoadedGitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel.LoadingState

private val LOG = logger<GitLabMergeRequestDetailsLoadingViewModel>()

internal interface GitLabMergeRequestDetailsLoadingViewModel {
  val mergeRequestLoadingFlow: Flow<LoadingState>

  fun requestLoad()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val detailsVm: GitLabMergeRequestDetailsViewModelImpl) : LoadingState
  }
}

internal class GitLabMergeRequestDetailsLoadingViewModelImpl(
  parentScope: CoroutineScope,
  currentUser: GitLabUserDTO,
  api: GitLabApi,
  projectData: GitLabProject,
  private val mergeRequestId: GitLabMergeRequestId
) : GitLabMergeRequestDetailsLoadingViewModel {
  private val scope = parentScope.childScope(Dispatchers.Default)

  private val loadingRequests = MutableSharedFlow<Unit>(1)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val mergeRequestLoadingFlow: Flow<LoadingState> = loadingRequests.transformLatest {
    emit(LoadingState.Loading)

    coroutineScope {
      val result = try {
        val data = scope.async(Dispatchers.IO) {
          api.loadMergeRequest(projectData.coordinates, mergeRequestId)
        }
        val mergeRequest = LoadedGitLabMergeRequest(scope, api, projectData.coordinates, data.await().body()!!)
        val detailsVm = GitLabMergeRequestDetailsViewModelImpl(scope, currentUser, projectData, mergeRequest)
        LoadingState.Result(detailsVm)
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LoadingState.Error(e)
      }
      emit(result)
      awaitCancellation()
    }
  }.modelFlow(scope, LOG)

  override fun requestLoad() {
    scope.launch {
      loadingRequests.emit(Unit)
    }
  }
}