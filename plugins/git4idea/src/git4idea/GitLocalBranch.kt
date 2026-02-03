// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository

/**
 * [GitLocalBranch] can't be simply moved to the shared module before dealing with [GitRepository]
 * and all related classes without breaking compatibility.
 *
 * Consider using [GitStandardLocalBranch] for new code
 */
class GitLocalBranch(name: String) : GitStandardLocalBranch(name) {
  fun findTrackedBranch(repository: GitRepository): GitRemoteBranch? {
    val info = GitBranchUtil.getTrackInfoForBranch(repository, this)
    return info?.remoteBranch
  }
}
