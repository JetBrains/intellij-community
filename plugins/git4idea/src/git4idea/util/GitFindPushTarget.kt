// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util

import git4idea.GitLocalBranch
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushTarget
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

fun findPushTarget(repository: GitRepository, remote: GitRemote, branch: GitLocalBranch) =
  GitPushTarget.getFromPushSpec(repository, remote, branch)
  ?: GitBranchUtil.getTrackInfoForBranch(repository, branch)
    ?.takeIf { it.remote == remote }
    ?.let { GitPushTarget(it.remoteBranch, false) }