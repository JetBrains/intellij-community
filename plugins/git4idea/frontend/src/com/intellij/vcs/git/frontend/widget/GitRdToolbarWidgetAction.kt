// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.actions.GitDataKeys
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.widget.GitToolbarWidgetActionBase
import com.intellij.vcs.git.shared.widget.actions.GitBranchesWidgetKeys
import git4idea.GitStandardLocalBranch

class GitRdToolbarWidgetAction: GitToolbarWidgetActionBase() {
  override fun getPopupForRepo(project: Project, repositoryId: RepositoryId): JBPopup? {
    val repositories = GitRepositoriesFrontendHolder.getInstance(project).getAll()

    val refs = repositories.flatMap {
      buildList {
        add(it.repositoryId)
        addAll(it.state.refs.localBranches)
        addAll(it.state.refs.remoteBranches)
      }
    }

    val baseListPopupStep = object : BaseListPopupStep<Any>(null, refs) {
      override fun isSpeedSearchEnabled() = true

      override fun getTextFor(value: Any?): String {
        return value?.toString().orEmpty()
      }

      override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<*>? {
        if (selectedValue is GitStandardLocalBranch) {
          val action = ActionManager.getInstance().getAction("Git.Rename.Local.Branch")
          val place = ActionPlaces.getPopupPlace("GitBranchesPopup.TopLevel.Branch.Actions")
          val dataContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[CommonDataKeys.PROJECT] = project
            sink[GitDataKeys.SELECTED_REF] = selectedValue
            sink[GitBranchesWidgetKeys.SELECTED_REPOSITORY] = repositories.firstOrNull()
            sink[GitBranchesWidgetKeys.AFFECTED_REPOSITORIES] = repositories
          }
          ActionUtil.invokeAction(action, dataContext, place, null, null)
        }

        return FINAL_CHOICE
      }
    }

    return JBPopupFactory.getInstance().createListPopup(baseListPopupStep)
  }

  override fun getPopupForUnknownGitRepo(project: Project, event: AnActionEvent): JBPopup? = null

  override fun doUpdate(e: AnActionEvent, project: Project) {
    if (!Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.doUpdate(e, project)
  }
}