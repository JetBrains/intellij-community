// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitRemoteBranch
import git4idea.repo.GitRepository

/**
 * Pushes the selected log commits to the tracked remote branch without a target selection.
 * Falls back to the remote branch selection popup when the current branch tracks no remote branch.
 *
 * The action does not fetch the target branch. The push base is the merge base of `HEAD`
 * and the local remote-tracking ref. The pushed result is the current `HEAD` minus
 * the commits that are not selected. When the branches have diverged, the push needs force.
 */
internal class GitPushSelectedCommitsAction : GitAddCommitToRemoteBranchAction() {

  override val baseMode: GitAddCommitToRemoteBranchOperation.BaseMode
    get() = GitAddCommitToRemoteBranchOperation.BaseMode.MERGE_BASE_WITH_HEAD

  override fun selectTargetBranch(
    project: Project,
    repository: GitRepository,
    commits: List<VcsFullCommitDetails>,
    onSelected: (GitRemoteBranch) -> Unit,
  ) {
    val trackedBranch = repository.currentBranch?.findTrackedBranch(repository)
    if (trackedBranch != null) {
      onSelected(trackedBranch)
    }
    else {
      super.selectTargetBranch(project, repository, commits, onSelected)
    }
  }
}
