// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

@Service(Service.Level.PROJECT)
class GitLabMergeRequestOnCurrentBranchService(project: Project, cs: CoroutineScope) {

  @OptIn(ExperimentalCoroutinesApi::class)
  private val mergeRequestIdState: StateFlow<String?> =
    project.service<GitLabToolWindowViewModel>().projectVm.flatMapLatest {
      it?.mergeRequestOnCurrentBranch ?: flowOf(null)
    }.stateIn(cs, SharingStarted.Eagerly, null)

  class BranchPresenter : GitCurrentBranchPresenter {
    override fun getPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation? {
      val mergeRequestIid = repository.project.service<GitLabMergeRequestOnCurrentBranchService>().mergeRequestIdState.value ?: return null
      val currentBranchName = StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
        GitBranchPopupActions.truncateBranchName(branchName, repository.project)
      })
      val text = GitLabBundle.message("merge.request.on.branch", mergeRequestIid, currentBranchName)
      return GitCurrentBranchPresenter.Presentation(
        GitlabIcons.GitLabLogo,
        text,
        null
      )
    }
  }

  class ShowAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val project = e.project
      val action = project?.service<GitLabToolWindowViewModel>()?.projectVm?.value?.takeIf {
        it.mergeRequestOnCurrentBranch.value != null
      }?.let {
        { it.showMergeRequestOnCurrentBranch() }
      }
      e.presentation.isEnabledAndVisible = action != null
      // required for thread safety
      e.presentation.putClientProperty(actionKey, action)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.getClientProperty(actionKey)?.invoke()
    }
  }
}

private val actionKey = Key.create<() -> Unit>("ShowAction.Action")