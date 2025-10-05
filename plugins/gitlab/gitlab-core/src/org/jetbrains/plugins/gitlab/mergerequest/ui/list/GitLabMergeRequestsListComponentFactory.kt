// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils
import com.intellij.collaboration.ui.codereview.list.*
import com.intellij.collaboration.ui.codereview.list.ReviewListItemPresentation.CommentsCounter
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal object GitLabMergeRequestsListComponentFactory {
  fun create(
    listModel: CollectionListModel<GitLabMergeRequestDetails>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ): JBList<GitLabMergeRequestDetails> {
    return ReviewListComponentFactory(listModel).create { mergeRequest ->
      ReviewListItemPresentation.Simple(
        title = mergeRequest.title,
        id = "!${mergeRequest.iid}",
        createdDate = mergeRequest.createdAt,
        author = userPresentation(mergeRequest.author, avatarIconsProvider),
        tagGroup = NamedCollection.create(
          GitLabBundle.message("merge.request.list.renderer.labels.popup", mergeRequest.labels.size),
          mergeRequest.labels.map(::getLabelPresentation)
        ),
        mergeableStatus = getMergeableStatus(mergeRequest.mergeStatus),
        buildStatus = null,
        state = getMergeStateText(mergeRequest.state, mergeRequest.draft),
        userGroup1 = NamedCollection.create(
          GitLabBundle.message("merge.request.list.renderer.user.assignees", mergeRequest.assignees.size),
          mergeRequest.assignees.map { assignee -> userPresentation(assignee, avatarIconsProvider) }
        ),
        userGroup2 = NamedCollection.create(
          GitLabBundle.message("merge.request.list.renderer.user.reviewers", mergeRequest.reviewers.size),
          mergeRequest.reviewers.map { reviewer -> userPresentation(reviewer, avatarIconsProvider) }
        ),
        commentsCounter = mergeRequest.userNotesCount?.let {
          CommentsCounter(it, GitLabBundle.message("merge.request.list.renderer.commentCount.tooltip", it))
        }
      )
    }
  }

  private fun getLabelPresentation(label: String): TagPresentation {
    return TagPresentation.Simple(label, null)
  }

  private fun userPresentation(user: GitLabUserDTO, avatarIconsProvider: IconsProvider<GitLabUserDTO>): UserPresentation {
    return UserPresentation.Simple(
      username = user.username,
      fullName = user.name,
      avatarIcon = avatarIconsProvider.getIcon(user, CodeReviewAvatarUtils.expectedIconHeight(Avatar.Sizes.OUTLINED))
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