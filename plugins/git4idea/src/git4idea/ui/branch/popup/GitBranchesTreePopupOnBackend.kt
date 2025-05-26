// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.widget.popup.GitBranchesTreePopup
import com.intellij.vcs.git.shared.widget.popup.GitBranchesTreePopupStep
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import org.apache.http.annotation.Obsolete
import org.jetbrains.annotations.VisibleForTesting

/**
 * Set of helper methods to create [GitBranchesTreePopup] using backend-only [GitRepository] model
 */
@Obsolete
internal object GitBranchesTreePopupOnBackend {
  /**
   * @param selectedRepository - Selected repository:
   * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
   */
  @JvmStatic
  fun show(project: Project, selectedRepository: GitRepository?) {
    create(project, selectedRepository).showCenteredInCurrentWindow(project)
  }

  /**
   * @param selectedRepository - Selected repository:
   * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
   */
  @JvmStatic
  fun create(project: Project, selectedRepository: GitRepository?): JBPopup {
    return GitBranchesTreePopup(project, createBranchesTreePopupStep(project, selectedRepository))
      .apply { setIsMovable(true) }
  }

  @VisibleForTesting
  internal fun createBranchesTreePopupStep(project: Project, selectedRepository: GitRepository?): GitBranchesTreePopupStep {
    val holder = GitRepositoriesFrontendHolder.getInstance(project)
    val repositories = holder.getAll().sorted()

    val selectedRepoIfNeeded =
      if (GitVcsSettings.getInstance(project).shouldExecuteOperationsOnAllRoots()) null
      else selectedRepository?.rpcId?.let { holder.get(it) }

    return GitBranchesTreePopupStep(project, selectedRepoIfNeeded, repositories, true)
  }
}
