// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewTabViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal sealed interface GitLabReviewTabViewModel : ReviewTabViewModel {
  class Details(
    parentCs: CoroutineScope,
    reviewId: String,
    detailsVm: Flow<Result<GitLabMergeRequestDetailsViewModel>>
  ) : GitLabReviewTabViewModel, Disposable {
    private val cs = parentCs.childScope().cancelledWith(this)

    override val displayName: @NlsSafe String = "!${reviewId}"

    val detailsVm: GitLabMergeRequestDetailsLoadingViewModel = GitLabMergeRequestDetailsLoadingViewModelImpl(cs, reviewId, detailsVm)

    override fun dispose() = Unit
  }

  class CreateMergeRequest(
    project: Project,
    parentCs: CoroutineScope,
    projectsManager: GitLabProjectsManager,
    projectData: GitLabProject,
    avatarIconProvider: IconsProvider<GitLabUserDTO>,
    openReviewTabAction: suspend (mrIid: String) -> Unit,
    onReviewCreated: () -> Unit
  ) : GitLabReviewTabViewModel, Disposable {
    private val cs = parentCs.childScope().cancelledWith(this)

    private val projectPath = projectData.projectMapping.repository.projectPath.fullPath()
    override val displayName: String = GitLabBundle.message("merge.request.create.tab.title", projectPath)

    val createVm = GitLabMergeRequestCreateViewModelImpl(
      project, cs,
      projectsManager, projectData, avatarIconProvider,
      openReviewTabAction, onReviewCreated
    )

    override fun dispose() = Unit
  }
}