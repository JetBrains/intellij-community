// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDetailsLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader

object GithubPullRequestKeys {
  @JvmStatic
  val PULL_REQUESTS_LOADER =
    DataKey.create<GithubPullRequestsLoader>("org.jetbrains.plugins.github.pullrequest.loader")
  @JvmStatic
  val PULL_REQUESTS_DETAILS_LOADER =
    DataKey.create<GithubPullRequestsDetailsLoader>("org.jetbrains.plugins.github.pullrequest.details.loader")
  @JvmStatic
  val SELECTED_PULL_REQUEST = DataKey.create<GithubSearchedIssue>("org.jetbrains.plugins.github.pullrequest.selected")
  @JvmStatic
  val REPOSITORY = DataKey.create<GitRepository>("org.jetbrains.plugins.github.pullrequest.repository")
  @JvmStatic
  val REMOTE = DataKey.create<GitRemote>("org.jetbrains.plugins.github.pullrequest.remote")
  @JvmStatic
  val FULL_PATH = DataKey.create<GithubFullPath>("org.jetbrains.plugins.github.pullrequest.remote.fullpath")
  @JvmStatic
  val SERVER_PATH = DataKey.create<GithubServerPath>("org.jetbrains.plugins.github.pullrequest.server.path")
}