// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.model.getSelected
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModelsImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.LoadAllGitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel

/**
 * Collection of view models for different merge request views
 */
internal class GitLabMergeRequestViewModels(private val project: Project,
                                            parentCs: CoroutineScope,
                                            projectData: GitLabProject,
                                            private val mergeRequest: GitLabMergeRequest,
                                            private val projectVm: GitLabToolWindowProjectViewModel,
                                            currentUser: GitLabUserDTO) {
  private val cs = parentCs.childScope()

  private val diffBridge = GitLabMergeRequestDiffBridge()

  private val lazyDetailsVm = lazy {
    GitLabMergeRequestDetailsViewModelImpl(project, cs, currentUser, projectData, mergeRequest, projectVm.avatarIconProvider).also {
      setupDetailsVm(it)
    }
  }
  val detailsVm: GitLabMergeRequestDetailsViewModel by lazyDetailsVm

  val timelineVm: GitLabMergeRequestTimelineViewModel by lazy {
    LoadAllGitLabMergeRequestTimelineViewModel(project, cs, project.service(), currentUser, mergeRequest).also {
      setupTimelineVm(it)
    }
  }

  private val discussionsVms: GitLabMergeRequestDiscussionsViewModels by lazy {
    GitLabMergeRequestDiscussionsViewModelsImpl(project, cs, currentUser, mergeRequest)
  }

  val diffVm: GitLabMergeRequestDiffViewModel by lazy {
    GitLabMergeRequestDiffViewModelImpl(project, cs, currentUser, mergeRequest, diffBridge, discussionsVms,
                                        projectVm.avatarIconProvider).apply { setup() }
  }

  val editorReviewVm: GitLabMergeRequestEditorReviewViewModel by lazy {
    GitLabMergeRequestEditorReviewViewModel(cs, project, projectData.projectMapping, currentUser, mergeRequest, diffBridge,
                                            projectVm, discussionsVms, projectVm.avatarIconProvider)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun setupDetailsVm(vm: GitLabMergeRequestDetailsViewModelImpl) {
    cs.launchNow(Dispatchers.EDT) {
      vm.showTimelineRequests.collect {
        projectVm.filesController.openTimeline(mergeRequest.iid, true)
      }
    }

    val changeListVms = vm.changesVm.changeListVm.mapNotNull { it.result?.getOrNull() }
    cs.launchNow {
      changeListVms.flatMapLatest {
        it.changesSelection
      }.filterNotNull().collectLatest {
        diffBridge.setChanges(it)
      }
    }

    cs.launchNow(Dispatchers.EDT) {
      changeListVms.collectLatest {
        it.showDiffRequests.collectLatest {
          projectVm.filesController.openDiff(mergeRequest.iid, true)
        }
      }
    }
  }

  private fun setupTimelineVm(vm: LoadAllGitLabMergeRequestTimelineViewModel) {
    cs.launchNow {
      vm.diffRequests.collect { change ->
        diffBridge.setChanges(change)
        withContext(Dispatchers.EDT) {
          projectVm.filesController.openDiff(mergeRequest.iid, true)
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun GitLabMergeRequestDiffViewModelImpl.setup() {
    cs.launchNow {
      diffVm.flatMapLatest {
        it.result?.getOrNull()?.producers?.map { state -> (state.getSelected() as? CodeReviewDiffRequestProducer)?.change } ?: flowOf(null)
      }.collectLatest {
        if (lazyDetailsVm.isInitialized() && it != null) {
          lazyDetailsVm.value.changesVm.selectChange(it)
        }
      }
    }
  }
}
