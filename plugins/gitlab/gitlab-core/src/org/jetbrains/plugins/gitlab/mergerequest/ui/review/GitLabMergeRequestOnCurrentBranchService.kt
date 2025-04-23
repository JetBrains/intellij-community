// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.gitlab.icons.GitlabIcons
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@Service(Service.Level.PROJECT)
class GitLabMergeRequestOnCurrentBranchService(project: Project, cs: CoroutineScope) {

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val mergeRequestReviewVmState: StateFlow<GitLabMergeRequestEditorReviewViewModel?> by lazy {
    val toolWindowVm = project.service<GitLabProjectViewModel>()
    toolWindowVm.connectedProjectVm.flatMapLatest {
      it?.currentMergeRequestReviewVm ?: flowOf(null)
    }.onStart {
      toolWindowVm.loginIfPossible()
    }.stateIn(cs, SharingStarted.Eagerly, null)
  }

  class BranchPresenter : GitCurrentBranchPresenter {
    override fun getPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation? {
      val vm = repository.project.service<GitLabMergeRequestOnCurrentBranchService>().mergeRequestReviewVmState.value ?: return null
      val currentBranchName = StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
        GitBranchPopupActions.truncateBranchName(branchName, repository.project)
      })
      return when (vm.actualChangesState.value) {
        GitLabMergeRequestEditorReviewViewModel.ChangesState.Error -> GitCurrentBranchPresenter.PresentationData(
          GitlabIcons.GitLabLogo,
          GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
          GitLabBundle.message("merge.request.on.branch.error", vm.mergeRequestIid, currentBranchName)
        )
        GitLabMergeRequestEditorReviewViewModel.ChangesState.Loading -> GitCurrentBranchPresenter.PresentationData(
          CollaborationToolsUIUtil.animatedLoadingIcon,
          GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
          GitLabBundle.message("merge.request.on.branch.loading", vm.mergeRequestIid)
        )
        GitLabMergeRequestEditorReviewViewModel.ChangesState.NotLoaded,
        is GitLabMergeRequestEditorReviewViewModel.ChangesState.Loaded -> {
          if (vm.localRepositorySyncStatus.value?.getOrNull()?.incoming == true) {
            GitCurrentBranchPresenter.PresentationData(
              GitlabIcons.GitLabWarning,
              GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
              GitLabBundle.message("merge.request.on.branch.out.of.sync", vm.mergeRequestIid, currentBranchName)
            )
          }
          else {
            GitCurrentBranchPresenter.PresentationData(
              GitlabIcons.GitLabLogo,
              GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
              GitLabBundle.message("merge.request.on.branch.description", vm.mergeRequestIid, currentBranchName)
            )
          }
        }
      }.copy(syncStatus = GitBranchSyncStatus.calcForCurrentBranch(repository))
    }
  }

  class ShowAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.project?.let(::getCurrentVm) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.let(::getCurrentVm)?.showMergeRequest(GitLabStatistics.ToolWindowOpenTabActionPlace.ACTION)
    }
  }

  class UpdateAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible =
        e.project?.let(::getCurrentVm)?.localRepositorySyncStatus?.value?.getOrNull()?.incoming == true
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.let(::getCurrentVm)?.updateBranch()
    }
  }

  class ToggleReviewAction : DumbAwareAction(), Toggleable {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val vm = e.project?.let(::getCurrentVm)
      if (vm == null || vm.localRepositorySyncStatus.value?.getOrNull()?.incoming == true) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      Toggleable.setSelected(e.presentation, vm.discussionsViewOption.value != DiscussionsViewOption.DONT_SHOW)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.let(::getCurrentVm)?.toggleReviewMode()
    }
  }
}

private fun getCurrentVm(project: Project): GitLabMergeRequestEditorReviewViewModel? =
  project.serviceIfCreated<GitLabMergeRequestOnCurrentBranchService>()?.mergeRequestReviewVmState?.value
