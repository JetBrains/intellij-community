// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.ui.toolwindow.ReviewTabViewModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import kotlin.coroutines.cancellation.CancellationException

internal sealed interface GitLabReviewTabViewModel : ReviewTabViewModel {
  class Details(
    project: Project,
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    projectData: GitLabProject,
    reviewId: GitLabMergeRequestId
  ) : GitLabReviewTabViewModel {
    private val cs = parentCs.childScope()

    override val displayName: @NlsSafe String = "!${reviewId.iid}"

    val detailsVm: GitLabMergeRequestDetailsLoadingViewModel =
      GitLabMergeRequestDetailsLoadingViewModelImpl(
        project, cs, currentUser, projectData, reviewId
      )


    override suspend fun destroy() {
      try {
        cs.coroutineContext[Job]!!.cancelAndJoin()
      }
      catch (e: CancellationException) {
        // ignore, cuz we don't want to cancel the invoker
      }
    }
  }

  suspend fun destroy()
}