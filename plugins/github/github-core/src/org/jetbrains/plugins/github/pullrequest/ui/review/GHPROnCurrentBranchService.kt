// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications

@Service(Service.Level.PROJECT)
class GHPROnCurrentBranchService(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val vmState: StateFlow<GHPRBranchWidgetViewModel?> by lazy {
    val vm = project.service<GHPRProjectViewModel>()
    vm.connectedProjectVm.flatMapLatest { projectVm ->
      projectVm?.prOnCurrentBranch
        ?.mapNotNull { it?.result } // we're not interested in intermittent loading/canceled state
        ?.map { it.getOrNull() }
        ?.distinctUntilChanged()
        ?.transformLatest<GHPRIdentifier?, GHPRBranchWidgetViewModel?> { prOnCurrentBranch ->
          if (prOnCurrentBranch != null) {
            supervisorScope {
              val vm = projectVm.acquireBranchWidgetModel(prOnCurrentBranch, this)
              vm.showUpdateErrorsIn(this)
              emit(vm)
              awaitCancellation()
            }
          }
          else {
            emit(null)
          }
        }
      ?: flowOf(null)
    }.onStart {
      vm.loginIfPossible()
    }.stateIn(cs, SharingStarted.Eagerly, null)
  }

  class BranchPresenter : GitCurrentBranchPresenter {
    override fun getPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation? {
      val vm = repository.project.getCurrentVm() ?: return null
      val currentBranchName = StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
        GitBranchPopupActions.truncateBranchName(branchName, repository.project)
      })

      val syncStatus = GitBranchSyncStatus.calcForCurrentBranch(repository)
      if (vm.updateRequired.value) {
        return GitCurrentBranchPresenter.PresentationData(
          GithubIcons.GithubWarning,
          GithubBundle.message("pull.request.on.branch", vm.id.number, currentBranchName),
          GithubBundle.message("pull.request.on.branch.out.of.sync", vm.id.number, currentBranchName),
          syncStatus
        )
      }

      return when (val changesResult = vm.dataLoadingState.value.result) {
        null -> GitCurrentBranchPresenter.PresentationData(
          CollaborationToolsUIUtil.animatedLoadingIcon,
          GithubBundle.message("pull.request.on.branch", vm.id.number, currentBranchName),
          GithubBundle.message("pull.request.on.branch.loading", vm.id.number, currentBranchName)
        )
        else -> changesResult.fold(
          onSuccess = {
            GitCurrentBranchPresenter.PresentationData(
              AllIcons.Vcs.Vendors.Github,
              GithubBundle.message("pull.request.on.branch", vm.id.number, currentBranchName),
              GithubBundle.message("pull.request.on.branch.description", vm.id.number, currentBranchName)
            )
          },
          onFailure = {
            GitCurrentBranchPresenter.PresentationData(
              AllIcons.Vcs.Vendors.Github,
              GithubBundle.message("pull.request.on.branch", vm.id.number, currentBranchName),
              GithubBundle.message("pull.request.on.branch.error", vm.id.number, currentBranchName)
            )
          }
        )
      }.copy(syncStatus = syncStatus)
    }
  }

  class ShowAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.project?.getCurrentVm() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.getCurrentVm()?.showPullRequest()
    }
  }

  class UpdateAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.project?.getCurrentVm()?.updateRequired?.value == true
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.getCurrentVm()?.updateBranch()
    }
  }

  class ToggleReviewAction : DumbAwareAction(), Toggleable {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val vm = e.project?.getCurrentVm()
      e.presentation.isEnabledAndVisible = vm?.updateRequired?.value == false
      Toggleable.setSelected(e.presentation, vm?.editorReviewEnabled?.value ?: false)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.getCurrentVm()?.toggleEditorReview()
    }
  }

  private fun GHPRBranchWidgetViewModel.showUpdateErrorsIn(cs: CoroutineScope) {
    cs.launchNow {
      updateErrors.collect {
        withContext(Dispatchers.Main) {
          GithubNotifications.showError(project,
                                        GithubNotificationIdsHolder.PULL_REQUEST_BRANCH_UPDATE_FAILED,
                                        GithubBundle.message("pull.request.on.branch.update.failed.title"),
                                        it)
        }
      }
    }
  }
}

private fun Project.getCurrentVm() = service<GHPROnCurrentBranchService>().vmState.value

