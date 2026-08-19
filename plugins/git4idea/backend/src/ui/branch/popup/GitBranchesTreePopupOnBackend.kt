// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.vcs.git.branch.popup.GitBranchesPopup
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.apache.http.annotation.Obsolete
import org.jetbrains.annotations.ApiStatus

/**
 * Set of helper methods to create branches tree pop-up using backend-only [GitRepository] model
 */
@Obsolete
@ApiStatus.Internal
object GitBranchesTreePopupOnBackend {
  /**
   * @param selectedRepository - Selected repository:
   * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
   */
  @JvmStatic
  fun create(project: Project, selectedRepository: GitRepository?): JBPopup {
    val holder = GitRepositoriesHolder.getInstance(project)
    val repositories = runWithModalProgressBlocking(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
      holder.getRepositories()
    }
    val preferredSelection = selectedRepository?.let { holder.get(it.repositoryId()) }
    return GitBranchesPopup.createDefaultPopup(project, preferredSelection, repositories)
  }
}
