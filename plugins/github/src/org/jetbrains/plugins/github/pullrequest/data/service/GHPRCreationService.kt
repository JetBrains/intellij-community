// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import git4idea.GitRemoteBranch
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

interface GHPRCreationService {
  suspend fun createPullRequest(baseBranch: GitRemoteBranch,
                                headRepo: GHGitRepositoryMapping,
                                headBranch: GitRemoteBranch,
                                title: String,
                                description: String,
                                draft: Boolean): GHPullRequestShort

  suspend fun findOpenPullRequest(baseBranch: GitRemoteBranch?,
                                  headRepo: GHRepositoryPath,
                                  headBranch: GitRemoteBranch): GHPRIdentifier?
}