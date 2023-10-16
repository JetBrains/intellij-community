// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewTabViewModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.selectedChange
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal sealed interface GitLabReviewTabViewModel : ReviewTabViewModel {
  @OptIn(ExperimentalCoroutinesApi::class)
  class Details(
    project: Project,
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    projectData: GitLabProject,
    reviewId: String,
    diffBridge: GitLabMergeRequestDiffBridge,
    filesController: GitLabMergeRequestsFilesController
  ) : GitLabReviewTabViewModel {
    private val cs = parentCs.childScope()

    override val displayName: @NlsSafe String = "!${reviewId}"

    val detailsVm: GitLabMergeRequestDetailsLoadingViewModel =
      GitLabMergeRequestDetailsLoadingViewModelImpl(
        project, cs, currentUser, projectData, reviewId
      )

    init {
      val detailsVmFlow = detailsVm.mergeRequestLoadingFlow.mapLatest {
        (it as? GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result)?.detailsVm
      }.filterNotNull()

      cs.launchNow(Dispatchers.EDT) {
        detailsVmFlow.flatMapLatest {
          it.showTimelineRequests
        }.collect {
          filesController.openTimeline(reviewId, true)
        }
      }

      cs.launchNow {
        detailsVmFlow.collectLatest { detailsVm ->
          val changesVm = detailsVm.changesVm
          val changeListVms = changesVm.changeListVm.mapNotNull { it.getOrNull() }

          coroutineScope {
            launchNow {
              changeListVms.flatMapLatest {
                it.changesSelection
              }.filterNotNull().collectLatest {
                diffBridge.setChanges(it)
              }
            }

            launchNow {
              diffBridge.displayedChanges.mapNotNull {
                (it as? ChangesSelection.Precise)?.selectedChange
              }.collect {
                changesVm.selectChange(it)
              }
            }

            launchNow(Dispatchers.EDT) {
              changeListVms.collectLatest {
                it.showDiffRequests.collectLatest {
                  filesController.openDiff(reviewId, true)
                }
              }
            }
            awaitCancellation()
          }
        }
      }
    }


    override suspend fun destroy() = cs.cancelAndJoinSilently()
  }

  class CreateMergeRequest(
    project: Project,
    parentCs: CoroutineScope,
    projectsManager: GitLabProjectsManager,
    projectData: GitLabProject,
    avatarIconProvider: IconsProvider<GitLabUserDTO>,
    openReviewTabAction: suspend (mrIid: String) -> Unit
  ) : GitLabReviewTabViewModel {
    private val cs = parentCs.childScope()

    private val projectPath = projectData.projectMapping.repository.projectPath.fullPath()
    override val displayName: String = GitLabBundle.message("merge.request.create.tab.title", projectPath)

    val createVm = GitLabMergeRequestCreateViewModelImpl(project, cs, projectsManager, projectData, avatarIconProvider, openReviewTabAction)

    override suspend fun destroy() = cs.cancelAndJoinSilently()
  }

  suspend fun destroy()
}