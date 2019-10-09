// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

class GHPRActionDataContext internal constructor(private val dataContext: GHPullRequestsDataContext,
                                                 private val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                 val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory) {

  val gitRepositoryCoordinates: GitRemoteUrlCoordinates = dataContext.gitRepositoryCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates = dataContext.repositoryCoordinates
  val requestExecutor: GithubApiRequestExecutor = dataContext.requestExecutor
  val currentUser: GHUser = dataContext.securityService.currentUser

  fun resetAllData() {
    dataContext.metadataService.resetData()
    dataContext.listLoader.reset()
    dataContext.dataLoader.invalidateAllData()
  }

  val pullRequest: Long?
    get() = selectionHolder.selectionNumber

  val pullRequestDetails: GHPullRequestShort?
    get() = pullRequest?.let { dataContext.listLoader.findData(it) }

  val pullRequestDataProvider: GithubPullRequestDataProvider?
    get() = pullRequest?.let { dataContext.dataLoader.getDataProvider(it) }

  companion object {
    @JvmStatic
    internal fun withFixedPullRequest(context: GHPRActionDataContext, pullRequest: Long): GHPRActionDataContext {
      return GHPRActionDataContext(context.dataContext, object : GithubPullRequestsListSelectionHolder {
        override var selectionNumber: Long?
          get() = pullRequest
          set(value) {}

        override fun addSelectionChangeListener(disposable: Disposable, listener: () -> Unit) {
        }
      }, context.avatarIconsProviderFactory)
    }
  }
}