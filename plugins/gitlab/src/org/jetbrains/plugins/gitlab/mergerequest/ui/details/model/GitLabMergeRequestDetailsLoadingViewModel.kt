// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestDetailsLoadingViewModelImpl(
  project: Project,
  parentScope: CoroutineScope,
  currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  private val mergeRequestId: GitLabMergeRequestId
) : GitLabMergeRequestDetailsLoadingViewModel {
  private val scope = parentScope.childScope(Dispatchers.Default)

  private val loadingRequests = MutableSharedFlow<Unit>(1)

  override val mergeRequestLoadingFlow: Flow<LoadingState> = loadingRequests.transformLatest {
    emit(LoadingState.Loading)
    projectData.mergeRequests.getShared(mergeRequestId).collectLatest {
      coroutineScope {
        val result = try {
          val detailsVm = GitLabMergeRequestDetailsViewModelImpl(project, scope, currentUser, projectData, it.getOrThrow())
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
    }
  }.modelFlow(scope, LOG)

  override fun requestLoad() {
    scope.launch {
      loadingRequests.emit(Unit)
    }
  }
}