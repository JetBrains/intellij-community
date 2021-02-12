// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.text.nullize
import git4idea.GitRemoteBranch
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRCreationServiceImpl(private val progressManager: ProgressManager,
                              private val requestExecutor: GithubApiRequestExecutor,
                              repositoryDataService: GHPRRepositoryDataService) : GHPRCreationService {

  private val baseRepo = repositoryDataService.repositoryMapping
  private val repositoryId = repositoryDataService.repositoryId

  override fun createPullRequest(progressIndicator: ProgressIndicator,
                                 baseBranch: GitRemoteBranch,
                                 headRepo: GHGitRepositoryMapping,
                                 headBranch: GitRemoteBranch,
                                 title: String,
                                 description: String,
                                 draft: Boolean): CompletableFuture<GHPullRequestShort> =
    progressManager.submitIOTask(progressIndicator) {
      it.text = GithubBundle.message("pull.request.create.process.title")

      val headRepositoryPrefix = if (baseRepo.repository == headRepo.repository) "" else headRepo.repository.repositoryPath.owner + ":"

      val actualTitle = title.takeIf(String::isNotBlank) ?: headBranch.nameForRemoteOperations
      val body = description.nullize(true)

      requestExecutor.execute(it, GHGQLRequests.PullRequest.create(baseRepo.repository, repositoryId,
                                                                   baseBranch.nameForRemoteOperations,
                                                                   headRepositoryPrefix + headBranch.nameForRemoteOperations,
                                                                   actualTitle, body, draft
      ))
    }
}