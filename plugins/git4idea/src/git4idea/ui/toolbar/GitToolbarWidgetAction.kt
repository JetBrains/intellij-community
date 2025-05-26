// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.ui.util.maximumWidth
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.widget.GitToolbarWidgetActionBase
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * TODO - Should be moved to the frontend and combined with [GitToolbarWidgetActionBase]
 */
@ApiStatus.Internal
class GitToolbarWidgetAction : GitToolbarWidgetActionBase() {
  override fun getPopupForRepo(project: Project, repositoryId: RepositoryId): JBPopup? {
    val repo = GitRepositoryManager.getInstance(project).repositories.find { it.rpcId == repositoryId } ?: return null
    return GitBranchesTreePopupOnBackend.create(project, repo)
  }

  override fun getPopupForUnknownGitRepo(project: Project, event: AnActionEvent): JBPopup? {
    val repo = runWithModalProgressBlocking(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
      project.serviceAsync<VcsRepositoryManager>().ensureUpToDate()
      coroutineToIndicator {
        GitBranchUtil.guessWidgetRepository(project, event.dataContext)
      }
    }

    return if (repo != null) GitBranchesTreePopupOnBackend.create(project, repo) else null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply { maximumWidth = Int.MAX_VALUE }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
  }

  override fun doUpdate(e: AnActionEvent, project: Project) {
    if (Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.doUpdate(e, project)
  }

  @ApiStatus.Internal
  companion object {
    const val BRANCH_NAME_MAX_LENGTH: Int = 80
  }
}
