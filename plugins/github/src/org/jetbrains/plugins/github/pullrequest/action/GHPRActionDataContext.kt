// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.editor.Document
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

interface GHPRActionDataContext {
  val account: GithubAccount

  val requestExecutor: GithubApiRequestExecutor

  val gitRepositoryCoordinates: GitRemoteUrlCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates

  val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory
  val currentUser: GHUser

  val pullRequestDetails: GHPullRequestShort
  val pullRequestDataProvider: GHPRDataProvider

  val submitReviewCommentDocument: Document
}