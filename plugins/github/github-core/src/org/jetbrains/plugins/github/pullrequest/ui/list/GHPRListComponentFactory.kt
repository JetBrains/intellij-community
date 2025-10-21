// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.list.*
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBList
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState
import org.jetbrains.plugins.github.pullrequest.ui.GHReviewersUtils
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GithubSettings
import javax.swing.ListModel

internal class GHPRListComponentFactory(
  private val interactionStateService: GHPRPersistentInteractionState,
  private val listModel: ListModel<GHPullRequestShort>
) {

  fun create(avatarIconsProvider: GHAvatarIconsProvider, ghostUser: GHUser, currentUser: GHUser): JBList<GHPullRequestShort> {
    return ReviewListComponentFactory(listModel).create {
      presentPR(avatarIconsProvider, it, ghostUser, currentUser)
    }.also {
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> it.selectedValue?.prId
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> it.selectedValue?.url
          else -> null
        }
      }
      val actionGroup = ActionManager.getInstance().getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup
      PopupHandler.installPopupMenu(it, actionGroup, ActionPlaces.POPUP)
      val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
      ActionUtil.wrap("Github.PullRequest.Show").registerCustomShortcutSet(shortcuts, it)
    }
  }

  private fun presentPR(
    avatarIconsProvider: GHAvatarIconsProvider,
    pr: GHPullRequestShort,
    ghostUser: GHUser,
    currentUser: GHUser
  ): ReviewListItemPresentation {
    val isUnreadDotEnabled = GithubSettings.getInstance().isSeenMarkersEnabled
    return ReviewListItemPresentation.Simple(
      pr.title, "#" + pr.number, pr.createdAt,
      author = createUserPresentation(avatarIconsProvider, pr.author),
      tagGroup = NamedCollection.create(GithubBundle.message("pull.request.labels.popup", pr.labels.size),
                                        pr.labels.map(::getLabelPresentation)),
      mergeableStatus = getMergeableStatus(pr.mergeable),
      state = getStateText(pr.state, pr.isDraft),
      userGroup1 = getAssigneesPresentation(avatarIconsProvider, pr.assignees),
      userGroup2 = getReviewersPresentation(
        avatarIconsProvider,
        GHReviewersUtils.getReviewsByReviewers(
          pr.author,
          pr.reviews,
          pr.reviewRequests.mapNotNull(GHPullRequestReviewRequest::requestedReviewer),
          ghostUser
        )
      ),
      commentsCounter = ReviewListItemPresentation.CommentsCounter(
        pr.unresolvedReviewThreadsCount,
        GithubBundle.message("pull.request.unresolved.comments", pr.unresolvedReviewThreadsCount)
      ),
      seen = if (isUnreadDotEnabled) interactionStateService.isSeen(pr, currentUser) else null
    )
  }

  private fun getLabelPresentation(label: GHLabel) =
    TagPresentation.Simple(label.name, ColorHexUtil.fromHex(label.color))

  private fun getStateText(state: GHPullRequestState, isDraft: Boolean): @NlsSafe String? {
    if (state == GHPullRequestState.OPEN && !isDraft) return null
    return GHUIUtil.getPullRequestStateText(state, isDraft)
  }

  private fun getMergeableStatus(mergeableState: GHPullRequestMergeableState): ReviewListItemPresentation.Status? {
    if (mergeableState == GHPullRequestMergeableState.CONFLICTING) {
      return ReviewListItemPresentation.Status(CollaborationToolsIcons.Review.NonMergeable,
                                               GithubBundle.message("pull.request.conflicts.merge.tooltip"))
    }

    return null
  }

  private fun getAssigneesPresentation(avatarIconsProvider: GHAvatarIconsProvider,
                                       assignees: List<GHUser>): NamedCollection<UserPresentation>? {
    return NamedCollection.create(GithubBundle.message("pull.request.assignees.popup", assignees.size),
                                  assignees.map { user -> createUserPresentation(avatarIconsProvider, user, null) })
  }

  private fun getReviewersPresentation(
    avatarIconsProvider: GHAvatarIconsProvider,
    reviewsByReviewers: Map<GHPullRequestRequestedReviewer, ReviewState>
  ): NamedCollection<UserPresentation>? {
    val presentations = createUserPresentationByFilter(avatarIconsProvider, reviewsByReviewers, ReviewState.ACCEPTED) +
                        createUserPresentationByFilter(avatarIconsProvider, reviewsByReviewers, ReviewState.WAIT_FOR_UPDATES) +
                        createUserPresentationByFilter(avatarIconsProvider, reviewsByReviewers, ReviewState.NEED_REVIEW)

    return NamedCollection.create(GithubBundle.message("pull.request.reviewers.popup", presentations.size), presentations)
  }

  private fun createUserPresentation(avatarIconsProvider: GHAvatarIconsProvider, user: GHActor?): UserPresentation? {
    if (user == null) return null
    return UserPresentation.Simple(user.login, null, avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.BASE))
  }

  private fun createUserPresentationByFilter(
    avatarIconsProvider: GHAvatarIconsProvider,
    reviewsByReviewers: Map<GHPullRequestRequestedReviewer, ReviewState>,
    reviewStateFilter: ReviewState
  ): List<UserPresentation> {
    return reviewsByReviewers
      .filterValues { reviewState -> reviewState == reviewStateFilter }
      .keys
      .map { reviewer -> createUserPresentation(avatarIconsProvider, reviewer, reviewStateFilter) }
  }

  private fun createUserPresentation(
    avatarIconsProvider: GHAvatarIconsProvider,
    user: GHPullRequestRequestedReviewer,
    reviewState: ReviewState?
  ): UserPresentation {
    val outlineColor = reviewState?.let(ReviewDetailsUIUtil::getReviewStateIconBorder)
    val avatarIcon = avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.OUTLINED)
    val icon = when (outlineColor) {
      null -> avatarIcon
      else -> CodeReviewAvatarUtils.createIconWithOutline(avatarIcon, outlineColor)
    }

    return UserPresentation.Simple(user.shortName, user.name, icon)
  }
}