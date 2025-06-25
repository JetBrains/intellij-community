// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GitBranchesUIHandler {
  fun compare(repositories: List<GitRepository>, branchName: String, otherBranchName: String = "")
}

private class GitBranchesUIHandlerImpl(
  private val project: Project,
) : GitBranchesUIHandler {
  override fun compare(repositories: List<GitRepository>, branchName: String, otherBranchName: String) {
    val ui = GitCompareBranchesUi(project, repositories, branchName)
    VcsProjectLog.runWhenLogIsReady(project) {
      GitCompareBranchesFilesManager.getInstance(project).openFile(ui, true)
    }
  }
}
