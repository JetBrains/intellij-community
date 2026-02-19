// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitUtil.HEAD
import git4idea.actions.branch.GitBranchActionsUtil.getRepositoriesForTopLevelActions
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.createOrCheckoutNewBranch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class GitCreateNewBranchAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    when (val data = collectData(e)) {
      is Data.Enabled -> createOrCheckoutNewBranch(data.project, data.repositories, HEAD,
                                                   initialName = data.repositories.getCommonCurrentBranch())
      is Data.Disabled, Data.Invisible -> {}
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    when (val data = collectData(e)) {
      is Data.Invisible -> e.presentation.isEnabledAndVisible = false
      is Data.Disabled -> {
        e.presentation.isVisible = true
        e.presentation.isEnabled = false
        e.presentation.description = data.description
      }
      else -> e.presentation.isEnabledAndVisible = true
    }
  }

  private sealed class Data {
    data object Invisible : Data()
    class Disabled(val description: @Nls String) : Data()
    class Enabled(val project: Project, val repositories: List<GitRepository>) : Data()
  }

  private fun collectData(e: AnActionEvent): Data {
    val project = e.project ?: return Data.Invisible
    val repositories = getRepositoriesForTopLevelActions(e)
    if (repositories.any { it.isFresh }) {
      return Data.Disabled(GitBundle.message("action.New.Branch.disabled.fresh.description"))
    }
    if (repositories.isEmpty()) return Data.Invisible
    return Data.Enabled(project, repositories)
  }
}
