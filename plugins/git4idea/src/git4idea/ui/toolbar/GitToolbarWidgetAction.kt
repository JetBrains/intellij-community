// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.widget.GitToolbarWidgetActionBase
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend

internal class GitToolbarWidgetAction : GitToolbarWidgetActionBase() {
  override fun getPopupForUnknownGitRepo(project: Project, event: AnActionEvent): JBPopup? {
    val repo = runWithModalProgressBlocking(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
      project.serviceAsync<VcsRepositoryManager>().ensureUpToDate()
      coroutineToIndicator {
        GitBranchUtil.guessWidgetRepository(project, event.dataContext)
      }
    }

    return if (repo != null) GitBranchesTreePopupOnBackend.create(project, repo) else null
  }

  override fun doUpdate(e: AnActionEvent, project: Project) {
    if (Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.doUpdate(e, project)
  }
}
