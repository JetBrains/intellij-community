// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository

class GitLocalBranch(name: String) : GitBranch(name) {
  override val isRemote: Boolean
    get() = false

  fun findTrackedBranch(repository: GitRepository): GitRemoteBranch? {
    val info = GitBranchUtil.getTrackInfoForBranch(repository, this)
    return info?.remoteBranch
  }

  override fun compareTo(o: GitReference?): Int {
    if (o is GitLocalBranch) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }
}
