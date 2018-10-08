// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.GithubPullRequestsComponentFactory
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider

object GithubPullRequestKeys {
  @JvmStatic
  val API_REQUEST_EXECUTOR =
    DataKey.create<GithubApiRequestExecutor>("org.jetbrains.plugins.github.pullrequest.requestexecutor")
  @JvmStatic
  internal val PULL_REQUESTS_COMPONENT =
    DataKey.create<GithubPullRequestsComponentFactory.GithubPullRequestsComponent>("org.jetbrains.plugins.github.pullrequest.component")
  @JvmStatic
  val SELECTED_PULL_REQUEST = DataKey.create<GithubSearchedIssue>("org.jetbrains.plugins.github.pullrequest.selected")
  @JvmStatic
  val SELECTED_PULL_REQUEST_DATA_PROVIDER =
    DataKey.create<GithubPullRequestDataProvider>("org.jetbrains.plugins.github.pullrequest.selected.dataprovider")
  @JvmStatic
  val REPOSITORY = DataKey.create<GitRepository>("org.jetbrains.plugins.github.pullrequest.repository")
  @JvmStatic
  val REMOTE = DataKey.create<GitRemote>("org.jetbrains.plugins.github.pullrequest.remote")
  @JvmStatic
  val REPO_DETAILS = DataKey.create<GithubRepoDetailed>("org.jetbrains.plugins.github.pullrequest.remote.repo.details")
  @JvmStatic
  val SERVER_PATH = DataKey.create<GithubServerPath>("org.jetbrains.plugins.github.pullrequest.server.path")
}