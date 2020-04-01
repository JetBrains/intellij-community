// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import javax.swing.ListModel

internal class GHPRDataContext(val gitRepositoryCoordinates: GitRemoteUrlCoordinates,
                               val repositoryCoordinates: GHRepositoryCoordinates,
                               val account: GithubAccount,
                               val requestExecutor: GithubApiRequestExecutor,
                               val messageBus: MessageBus,
                               val listModel: ListModel<GHPullRequestShort>,
                               val searchHolder: GithubPullRequestSearchQueryHolder,
                               val listLoader: GHPRListLoader,
                               val dataLoader: GHPRDataLoader,
                               val securityService: GHPRSecurityService,
                               val metadataService: GHPRMetadataService,
                               val stateService: GHPRStateService,
                               val reviewService: GHPRReviewService,
                               val commentService: GHPRCommentService) : Disposable {

  override fun dispose() {
    Disposer.dispose(messageBus)
    Disposer.dispose(dataLoader)
    Disposer.dispose(listLoader)
    Disposer.dispose(metadataService)
  }

  companion object {
    val PULL_REQUEST_EDITED_TOPIC = Topic(PullRequestEditedListener::class.java)

    interface PullRequestEditedListener {
      fun onPullRequestEdited(id: GHPRIdentifier) {}
      fun onPullRequestReviewsEdited(id: GHPRIdentifier) {}
      fun onPullRequestCommentsEdited(id: GHPRIdentifier) {}
    }
  }
}
