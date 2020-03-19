// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.editor.EditorFactory
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider

class GHPRFixedActionDataContext internal constructor(dataContext: GHPRDataContext,
                                                      dataProvider: GHPRDataProvider,
                                                      override val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                                      private val detailsProvider: () -> GHPullRequestShort)
  : GHPRActionDataContext {

  override val account = dataContext.account

  override val gitRepositoryCoordinates = dataContext.gitRepositoryCoordinates
  override val repositoryCoordinates = dataContext.repositoryCoordinates

  override val securityService = dataContext.securityService
  override val stateService = dataContext.stateService
  override val reviewService = dataContext.reviewService
  override val commentService = dataContext.commentService

  override val requestExecutor = dataContext.requestExecutor

  override val currentUser = dataContext.securityService.currentUser

  override val pullRequestDetails
    get() = detailsProvider()
  override val pullRequestDataProvider = dataProvider

  override val submitReviewCommentDocument by lazy(LazyThreadSafetyMode.NONE) { EditorFactory.getInstance().createDocument("") }
}