// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitRemoteBranch
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.util.concurrent.CompletableFuture

interface GHPRCreationService {

  @CalledInAny
  fun createPullRequest(progressIndicator: ProgressIndicator,
                        baseBranch: GitRemoteBranch,
                        headRepo: GHGitRepositoryMapping,
                        headBranch: GitRemoteBranch,
                        title: String,
                        description: String,
                        draft: Boolean): CompletableFuture<GHPullRequestShort>

  @RequiresBackgroundThread
  fun findPullRequest(progressIndicator: ProgressIndicator,
                      baseBranch: GitRemoteBranch,
                      headRepo: GHGitRepositoryMapping,
                      headBranch: GitRemoteBranch): GHPRIdentifier?
}