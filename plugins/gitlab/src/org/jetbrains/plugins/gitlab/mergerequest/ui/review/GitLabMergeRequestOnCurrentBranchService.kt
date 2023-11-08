// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

@Service(Service.Level.PROJECT)
class GitLabMergeRequestOnCurrentBranchService(project: Project, cs: CoroutineScope) {

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val mergeRequestReviewVmState: StateFlow<GitLabMergeRequestEditorReviewViewModel?> by lazy {
    val toolWindowVm = project.service<GitLabToolWindowViewModel>()
    toolWindowVm.projectVm.flatMapLatest {
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
        GitLabMergeRequestEditorReviewViewModel.ChangesState.Error -> GitCurrentBranchPresenter.Presentation(
          GitlabIcons.GitLabLogo,
          GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
          GitLabBundle.message("merge.request.on.branch.error", vm.mergeRequestIid, currentBranchName)
        )
        GitLabMergeRequestEditorReviewViewModel.ChangesState.Loading -> GitCurrentBranchPresenter.Presentation(
          CollaborationToolsUIUtil.animatedLoadingIcon,
          GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
          GitLabBundle.message("merge.request.on.branch.loading", vm.mergeRequestIid)
        )
        GitLabMergeRequestEditorReviewViewModel.ChangesState.NotLoaded,
        is GitLabMergeRequestEditorReviewViewModel.ChangesState.Loaded -> {
          if (vm.localRepositorySyncStatus.value?.incoming != true) {
            GitCurrentBranchPresenter.Presentation(
              GitlabIcons.GitLabLogo,
              GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
              GitLabBundle.message("merge.request.on.branch.description", vm.mergeRequestIid, currentBranchName)
            )
          }
          else {
            GitCurrentBranchPresenter.Presentation(
              GitlabIcons.GitLabWarning,
              GitLabBundle.message("merge.request.on.branch", vm.mergeRequestIid, currentBranchName),
              GitLabBundle.message("merge.request.on.branch.out.of.sync", vm.mergeRequestIid, currentBranchName)
            )
          }
        }
      }.copy(syncStatus = GitBranchSyncStatus.calcForCurrentBranch(repository))
    }
  }

  class ShowAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val action = e.project?.serviceIfCreated<GitLabMergeRequestOnCurrentBranchService>()?.mergeRequestReviewVmState?.value?.let {
        { it.showMergeRequest() }
      }
      e.presentation.isEnabledAndVisible = action != null
      // required for thread safety
      e.presentation.putClientProperty(actionKey, action)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.getClientProperty(actionKey)?.invoke()
    }
  }

  class UpdateAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val action = e.project?.serviceIfCreated<GitLabMergeRequestOnCurrentBranchService>()?.mergeRequestReviewVmState?.value
        ?.takeIf {
          it.localRepositorySyncStatus.value?.incoming == true
        }?.let {
          { it.updateBranch() }
        }
      e.presentation.isEnabledAndVisible = action != null
      // required for thread safety
      e.presentation.putClientProperty(actionKey, action)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.getClientProperty(actionKey)?.invoke()
    }
  }

  class ToggleReviewAction : DumbAwareAction(), Toggleable {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val vm = e.project?.serviceIfCreated<GitLabMergeRequestOnCurrentBranchService>()?.mergeRequestReviewVmState?.value
      if (vm == null || vm.localRepositorySyncStatus.value?.incoming == true) {
        e.presentation.isEnabledAndVisible = false
        e.presentation.putClientProperty(actionKey, null)
        return
      }

      Toggleable.setSelected(e.presentation, vm.discussionsViewOption.value != DiscussionsViewOption.DONT_SHOW)
      e.presentation.putClientProperty(actionKey) { vm.toggleReviewMode() }
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.getClientProperty(actionKey)?.invoke()
    }
  }
}

private val actionKey = Key.create<() -> Unit>("ShowAction.Action")