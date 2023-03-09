// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.ui.codereview.list.NamedCollection
import com.intellij.collaboration.ui.codereview.list.ReviewListComponentFactory
import com.intellij.collaboration.ui.codereview.list.ReviewListItemPresentation
import com.intellij.collaboration.ui.codereview.list.UserPresentation
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal object GitLabMergeRequestsListComponentFactory {
  private const val AVATAR_SIZE = 20

  fun create(
    listModel: CollectionListModel<GitLabMergeRequestShortDTO>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JBList<GitLabMergeRequestShortDTO> {
    return ReviewListComponentFactory(listModel).create { mergeRequest ->
      ReviewListItemPresentation.Simple(
        title = mergeRequest.title,
        id = "!${mergeRequest.iid}",
        createdDate = mergeRequest.createdAt,
        author = userPresentation(mergeRequest.author, avatarIconsProvider),
        tagGroup = null,
        mergeableStatus = getMergeableStatus(mergeRequest.mergeStatusEnum),
        buildStatus = null,
        state = getMergeStateText(mergeRequest.stateEnum, mergeRequest.draft),
        userGroup1 = NamedCollection.create(
          GitLabBundle.message("merge.request.list.renderer.user.assignees", mergeRequest.assignees.size),
          mergeRequest.assignees.map { assignee -> userPresentation(assignee, avatarIconsProvider) }
        ),
        userGroup2 = NamedCollection.create(
          GitLabBundle.message("merge.request.list.renderer.user.reviewers", mergeRequest.reviewers.size),
          mergeRequest.reviewers.map { reviewer -> userPresentation(reviewer, avatarIconsProvider) }
        ),
        commentsCounter = null
      )
    }
  }

  private fun userPresentation(user: GitLabUserDTO, avatarIconsProvider: IconsProvider<GitLabUserDTO>): UserPresentation {
    return UserPresentation.Simple(
      username = user.username,
      fullName = user.name,
      avatarIcon = avatarIconsProvider.getIcon(user, AVATAR_SIZE)
    )
  }

  private fun getMergeableStatus(mergeStatus: GitLabMergeStatus): ReviewListItemPresentation.Status? {
    if (mergeStatus == GitLabMergeStatus.CANNOT_BE_MERGED) {
      return ReviewListItemPresentation.Status(CollaborationToolsIcons.Review.NonMergeable,
                                               GitLabBundle.message("merge.request.list.renderer.merge.conflict.tooltip"))
    }

    return null
  }

  private fun getMergeStateText(mergeRequestState: GitLabMergeRequestState, isDraft: Boolean): @NlsSafe String? {
    if (mergeRequestState == GitLabMergeRequestState.OPENED && !isDraft) {
      return null
    }

    if (isDraft) {
      return GitLabBundle.message("merge.request.list.renderer.state.draft")
    }

    return when (mergeRequestState) {
      GitLabMergeRequestState.CLOSED -> GitLabBundle.message("merge.request.list.renderer.state.closed")
      GitLabMergeRequestState.MERGED -> GitLabBundle.message("merge.request.list.renderer.state.merged")
      else -> null
    }
  }
}