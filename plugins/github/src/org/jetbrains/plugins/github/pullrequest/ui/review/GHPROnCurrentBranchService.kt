// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.mapScoped
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

@Service(Service.Level.PROJECT)
class GHPROnCurrentBranchService(project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Default)

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val vmState: StateFlow<GHPROnCurrentBranchViewModel?> by lazy {
    val toolWindowVm = project.service<GHPRToolWindowViewModel>()
    toolWindowVm.projectVm.flatMapLatest { projectVm ->
      projectVm?.prOnCurrentBranch
        ?.mapNotNull { it?.result } // we're not interested in intermittent loading/canceled state
        ?.map { it.getOrNull() }
        ?.distinctUntilChanged()
        ?.mapScoped { prOnCurrentBranch ->
          prOnCurrentBranch?.let { projectVm.createPrOnCurrentBranchVmIn(this, it) }
        }
      ?: flowOf(null)
    }.onStart {
      toolWindowVm.loginIfPossible()
    }.stateIn(cs, SharingStarted.Eagerly, null)
  }

  class BranchPresenter : GitCurrentBranchPresenter {
    override fun getPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation? {
      val vm = repository.project.getCurrentVm() ?: return null
      val currentBranchName = StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
        GitBranchPopupActions.truncateBranchName(branchName, repository.project)
      })
      return GitCurrentBranchPresenter.Presentation(
        AllIcons.Vcs.Vendors.Github,
        GithubBundle.message("pull.request.on.branch", vm.id.number, currentBranchName),
        GithubBundle.message("pull.request.on.branch.description", vm.id.number, currentBranchName)
      ).copy(syncStatus = GitBranchSyncStatus.calcForCurrentBranch(repository))
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
}

private fun Project.getCurrentVm() = service<GHPROnCurrentBranchService>().vmState.value

