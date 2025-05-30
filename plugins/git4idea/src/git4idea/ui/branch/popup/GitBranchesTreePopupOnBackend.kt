// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import com.intellij.vcs.git.shared.repo.GitRepositoryFrontendModel
import com.intellij.vcs.git.shared.widget.popup.GitBranchesWidgetPopup
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import org.apache.http.annotation.Obsolete
import org.jetbrains.annotations.VisibleForTesting

/**
 * Set of helper methods to create branches tree pop-up using backend-only [GitRepository] model
 */
@Obsolete
internal object GitBranchesTreePopupOnBackend {
  /**
   * @param selectedRepository - Selected repository:
   * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
   */
  @JvmStatic
  fun create(project: Project, selectedRepository: GitRepository?): JBPopup {
    return GitBranchesWidgetPopup.createPopup(project, selectedRepoIfNeeded(selectedRepository))
  }

  @VisibleForTesting
  internal fun selectedRepoIfNeeded(selectedRepository: GitRepository?): GitRepositoryFrontendModel? = when {
    selectedRepository == null -> null
    GitVcsSettings.getInstance(selectedRepository.project).shouldExecuteOperationsOnAllRoots() -> null
    else -> GitRepositoriesFrontendHolder.getInstance(selectedRepository.project).get(selectedRepository.rpcId)
  }
}
