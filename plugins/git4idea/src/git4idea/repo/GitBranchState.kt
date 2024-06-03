// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.Repository
import com.intellij.vcs.log.Hash
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch

internal class GitBranchState(val currentRevision: String?,
                              val currentBranch: GitLocalBranch?,
                              val state: Repository.State,
                              val localBranches: Map<GitLocalBranch, Hash>,
                              val remoteBranches: Map<GitRemoteBranch, Hash>)
