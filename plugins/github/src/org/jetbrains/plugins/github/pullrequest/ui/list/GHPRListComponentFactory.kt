// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.ui.codereview.Avatar
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
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.ListModel

internal class GHPRListComponentFactory(private val listModel: ListModel<GHPullRequestShort>) {

  fun create(avatarIconsProvider: GHAvatarIconsProvider): JBList<GHPullRequestShort> {
    return ReviewListComponentFactory(listModel).create {
      presentPR(avatarIconsProvider, it)
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

  private fun presentPR(avatarIconsProvider: GHAvatarIconsProvider, pr: GHPullRequestShort) =
    ReviewListItemPresentation.Simple(pr.title, "#" + pr.number, pr.createdAt,
                                      createUserPresentation(avatarIconsProvider, pr.author),
                                      tagGroup = NamedCollection.create(GithubBundle.message("pull.request.labels.popup", pr.labels.size),
                                                                        pr.labels.map(::getLabelPresentation)),
                                      mergeableStatus = getMergeableStatus(pr.mergeable),
                                      state = getStateText(pr.state, pr.isDraft),
                                      userGroup1 = getAssigneesPresentation(avatarIconsProvider, pr.assignees),
                                      userGroup2 = getReviewersPresentation(avatarIconsProvider, pr.reviewRequests),
                                      commentsCounter = ReviewListItemPresentation.CommentsCounter(
                                        pr.unresolvedReviewThreadsCount,
                                        GithubBundle.message("pull.request.unresolved.comments", pr.unresolvedReviewThreadsCount)
                                      ))

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
                                  assignees.map { user -> createUserPresentation(avatarIconsProvider, user) })
  }

  private fun getReviewersPresentation(avatarIconsProvider: GHAvatarIconsProvider,
                                       reviewRequests: List<GHPullRequestReviewRequest>): NamedCollection<UserPresentation>? {
    val reviewers = reviewRequests.mapNotNull { it.requestedReviewer }
    return NamedCollection.create(GithubBundle.message("pull.request.reviewers.popup", reviewers.size),
                                  reviewers.map { reviewer -> createUserPresentation(avatarIconsProvider, reviewer) })
  }

  private fun createUserPresentation(avatarIconsProvider: GHAvatarIconsProvider, user: GHActor?): UserPresentation? {
    if (user == null) return null
    return UserPresentation.Simple(user.login, null, avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.BASE))
  }

  private fun createUserPresentation(avatarIconsProvider: GHAvatarIconsProvider, user: GHUser): UserPresentation =
    UserPresentation.Simple(user.login, user.name, avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.BASE))

  private fun createUserPresentation(avatarIconsProvider: GHAvatarIconsProvider, user: GHPullRequestRequestedReviewer): UserPresentation {
    return UserPresentation.Simple(user.shortName, user.name, avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.BASE))
  }
}