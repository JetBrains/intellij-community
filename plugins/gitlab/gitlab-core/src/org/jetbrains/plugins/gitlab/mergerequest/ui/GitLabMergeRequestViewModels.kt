// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.ListSelection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffProcessorViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
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
internal class GitLabMergeRequestViewModels(
  private val project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
  private val projectVm: GitLabToolWindowProjectViewModel,
  currentUser: GitLabUserDTO,
) {
  private val cs = parentCs.childScope(javaClass.name)

  private val lazyDetailsVm = lazy {
    GitLabMergeRequestDetailsViewModelImpl(project, cs, currentUser, projectData, mergeRequest, projectVm.avatarIconProvider).also {
      setupDetailsVm(it)
    }
  }
  val detailsVm: GitLabMergeRequestDetailsViewModel by lazyDetailsVm

  val timelineVm: GitLabMergeRequestTimelineViewModel by lazy {
    LoadAllGitLabMergeRequestTimelineViewModel(project, cs, projectData, project.service(), currentUser, mergeRequest).also {
      setupTimelineVm(it)
    }
  }

  private val discussionsVms: GitLabMergeRequestDiscussionsViewModels by lazy {
    GitLabMergeRequestDiscussionsViewModelsImpl(project, cs, projectData, currentUser, mergeRequest)
  }

  private val _diffVm by lazy {
    GitLabMergeRequestDiffProcessorViewModelImpl(project, cs, currentUser, mergeRequest, discussionsVms, projectVm.avatarIconProvider).apply {
      setup()
    }
  }
  val diffVm: GitLabMergeRequestDiffViewModel get() = _diffVm

  val editorReviewVm: GitLabMergeRequestEditorReviewViewModel by lazy {
    GitLabMergeRequestEditorReviewViewModel(cs, project, projectData.projectMapping, currentUser, mergeRequest,
                                            projectVm, discussionsVms, projectVm.avatarIconProvider).apply {
      setup()
    }
  }

  private fun setupDetailsVm(vm: GitLabMergeRequestDetailsViewModelImpl) {
    cs.launchNow(Dispatchers.EDT) {
      vm.showTimelineRequests.collect {
        projectVm.filesController.openTimeline(mergeRequest.iid, true)
      }
    }

    val changeListVms = vm.changesVm.changeListVm.mapNotNull { it.result?.getOrNull() }
    cs.launchNow {
      vm.changesVm.changeListVm.map { it.getOrNull() }.collectScoped { vm ->
        vm?.handleSelection {
          if (it != null) {
            _diffVm.showChanges(ListSelection.createAt(it.changes, it.selectedIdx))
          }
        }
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
      vm.diffRequests.collect {
        _diffVm.showChanges(ListSelection.createAt(it.changes, it.selectedIdx), it.location)
        withContext(Dispatchers.EDT) {
          projectVm.filesController.openDiff(mergeRequest.iid, true)
        }
      }
    }
  }

  private fun GitLabMergeRequestDiffProcessorViewModelImpl.setup() {
    cs.launchNow {
      handleSelection {
        val change = it?.selectedItem
        if (lazyDetailsVm.isInitialized() && change != null) {
          lazyDetailsVm.value.changesVm.selectChange(change)
        }
      }
    }
  }

  private fun GitLabMergeRequestEditorReviewViewModel.setup() {
    cs.launchNow {
      handleDiffRequests { listWithSelection, location ->
        _diffVm.showChanges(listWithSelection, location)
        runInEdt {
          projectVm.filesController.openDiff(mergeRequest.iid, true)
        }
      }
    }
  }
}
