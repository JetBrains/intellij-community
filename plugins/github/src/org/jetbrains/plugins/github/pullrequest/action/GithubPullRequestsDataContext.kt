// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsListLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsRepositoryDataLoader
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder

class GithubPullRequestsDataContext internal constructor(val requestExecutor: GithubApiRequestExecutor,
                                                         internal val repositoryDataLoader: GithubPullRequestsRepositoryDataLoader,
                                                         internal val listLoader: GithubPullRequestsListLoader,
                                                         internal val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                         internal val dataLoader: GithubPullRequestsDataLoader,
                                                         val serverPath: GithubServerPath,
                                                         val repositoryDetails: GithubRepoDetailed,
                                                         val accountDetails: GithubAuthenticatedUser,
                                                         val gitRepository: GitRepository,
                                                         val gitRemote: GitRemote) {

  val selectedPullRequest: Long?
    get() = selectionHolder.selectionNumber

  val selectedPullRequestDataProvider: GithubPullRequestDataProvider?
    get() = selectedPullRequest?.let(dataLoader::getDataProvider)

}