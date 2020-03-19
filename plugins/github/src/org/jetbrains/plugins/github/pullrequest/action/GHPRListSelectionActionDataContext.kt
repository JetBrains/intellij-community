// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.editor.EditorFactory
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder

class GHPRListSelectionActionDataContext internal constructor(private val dataContext: GHPRDataContext,
                                                              private val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                              override val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
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

  override fun resetAllData() {
    dataContext.metadataService.resetData()
    dataContext.listLoader.reset()
    dataContext.dataLoader.invalidateAllData()
  }

  override val pullRequestDetails: GHPullRequestShort?
    get() = selectionHolder.selection?.let { dataContext.listLoader.findData(it) }

  override val pullRequestDataProvider: GHPRDataProvider?
    get() = selectionHolder.selection?.let { dataContext.dataLoader.getDataProvider(it) }

  override val submitReviewCommentDocument by lazy(LazyThreadSafetyMode.NONE) { EditorFactory.getInstance().createDocument("") }
}