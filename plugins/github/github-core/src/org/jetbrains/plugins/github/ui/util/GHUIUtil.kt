// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import javax.swing.Icon

internal object GHUIUtil {
  fun getPullRequestStateIcon(state: GHPullRequestState, isDraft: Boolean): Icon =
    if (isDraft) GithubIcons.PullRequestDraft
    else when (state) {
      GHPullRequestState.CLOSED -> CollaborationToolsIcons.PullRequestClosed
      GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
      GHPullRequestState.OPEN -> CollaborationToolsIcons.PullRequestOpen
    }

  fun getPullRequestStateText(state: GHPullRequestState, isDraft: Boolean): @NlsSafe String =
    if (isDraft) CollaborationToolsBundle.message("review.details.review.state.draft")
    else when (state) {
      GHPullRequestState.CLOSED -> CollaborationToolsBundle.message("review.details.review.state.closed")
      GHPullRequestState.MERGED -> CollaborationToolsBundle.message("review.details.review.state.merged")
      GHPullRequestState.OPEN -> CollaborationToolsBundle.message("review.details.review.state.open")
    }

  fun createIssueLabelLabel(label: GHLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    background = CollaborationToolsUIUtil.getLabelBackground(label.color)
    foreground = CollaborationToolsUIUtil.getLabelForeground(background)
  }.andOpaque()

  object SelectionPresenters {
    fun PRReviewers(avatarIconsProvider: GHAvatarIconsProvider): (GHPullRequestRequestedReviewer) -> PopupItemPresentation.Simple = {
      PopupItemPresentation.Simple(
        it.shortName,
        avatarIconsProvider.getIcon(it.avatarUrl, Avatar.Sizes.BASE),
        null
      )
    }

    fun Users(avatarIconsProvider: GHAvatarIconsProvider): (GHUser) -> PopupItemPresentation.Simple = {
      PopupItemPresentation.Simple(
        it.login,
        avatarIconsProvider.getIcon(it.avatarUrl, Avatar.Sizes.BASE),
        null
      )
    }

    fun Labels(): (GHLabel) -> PopupItemPresentation.Simple = {
      PopupItemPresentation.Simple(
        it.name,
        ColorIcon(16, ColorUtil.fromHex(it.color)),
        null
      )
    }
  }

  @NlsSafe
  fun getRepositoryDisplayName(allRepositories: Collection<GHRepositoryCoordinates>,
                               repository: GHRepositoryCoordinates,
                               alwaysShowOwner: Boolean = false): String {
    val showServer = needToShowRepositoryServer(allRepositories)
    val showOwner = if (showServer || alwaysShowOwner) true else needToShowRepositoryOwner(allRepositories)

    val builder = StringBuilder()
    if (showServer) builder.append(repository.serverPath.toUrl(false)).append("/")
    if (showOwner) builder.append(repository.repositoryPath.owner).append("/")
    builder.append(repository.repositoryPath.repository)
    return builder.toString()
  }

  /**
   * Assuming all servers are the same
   */
  private fun needToShowRepositoryOwner(repos: Collection<GHRepositoryCoordinates>): Boolean {
    if (repos.size <= 1) return false
    val firstOwner = repos.first().repositoryPath.owner
    return repos.any { it.repositoryPath.owner != firstOwner }
  }

  private fun needToShowRepositoryServer(repos: Collection<GHRepositoryCoordinates>): Boolean {
    if (repos.size <= 1) return false
    val firstServer = repos.first().serverPath
    return repos.any { it.serverPath != firstServer }
  }
}