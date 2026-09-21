// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectBrancher
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.awt.RelativePoint
import com.intellij.vcs.git.branch.popup.GitBranchesPopup
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.findGitRoot
import git4idea.i18n.GitBundle
import git4idea.prepareRecentGitProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

internal class GitRecentProjectBrancher : RecentProjectBrancher {
  override fun canManageBranches(projectPath: Path): Boolean = findGitRoot(projectPath) != null

  override suspend fun showBranches(project: Project, projectPath: Path, anchor: RelativePoint?) {
    val repositories = withBackgroundProgress(project, GitBundle.message("action.Git.Loading.Branches.progress")) {
      loadBranchRepositories(project, projectPath)
    }
    withContext(Dispatchers.EDT) {
      val popup = GitBranchesPopup.createDefaultPopup(project, preferredSelection = null, repositories = repositories)
      if (anchor != null) popup.show(anchor) else popup.showInFocusCenter()
    }
  }

  @VisibleForTesting
  internal suspend fun loadBranchRepositories(project: Project, projectPath: Path): List<GitRepositoryModel> {
    prepareRecentGitProject(project, projectPath)
    val repositories = GitRepositoriesHolder.getInstance(project).getRepositories()
    check(repositories.isNotEmpty()) { "No Git repository found for the recent project '$projectPath'" }
    return repositories
  }
}
