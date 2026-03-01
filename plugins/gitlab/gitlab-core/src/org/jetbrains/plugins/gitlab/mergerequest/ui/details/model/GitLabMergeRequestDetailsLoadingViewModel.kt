// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel.LoadingState

@ApiStatus.Internal
interface GitLabMergeRequestDetailsLoadingViewModel {
  val mergeRequestId: String
  val mergeRequestLoadingFlow: Flow<LoadingState>

  fun reloadData()
  fun refreshData()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val detailsVm: GitLabMergeRequestDetailsViewModel) : LoadingState
  }
}

@ApiStatus.Internal
class GitLabMergeRequestDetailsLoadingViewModelImpl(
  parentScope: CoroutineScope,
  override val mergeRequestId: String,
  detailsVm: Flow<Result<GitLabMergeRequestDetailsViewModel>>
) : GitLabMergeRequestDetailsLoadingViewModel {
  private val scope = parentScope.childScope(this::class, Dispatchers.Default)

  override val mergeRequestLoadingFlow: Flow<LoadingState> = detailsVm.map { vmResult ->
    vmResult.map { LoadingState.Result(it) }.getOrElse { LoadingState.Error(it) }
  }.stateIn(scope, SharingStarted.Lazily, LoadingState.Loading)

  override fun reloadData() {
    scope.launch {
      mergeRequestLoadingFlow.first().asSafely<LoadingState.Result>()?.detailsVm?.reloadData()
    }
  }

  override fun refreshData() {
    scope.launch {
      mergeRequestLoadingFlow.first().asSafely<LoadingState.Result>()?.detailsVm?.refreshData()
    }
  }
}