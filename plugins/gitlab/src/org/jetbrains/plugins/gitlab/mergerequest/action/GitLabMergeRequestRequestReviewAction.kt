// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabProjectDetailsLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.JComponent

// TODO: implement scenario with multiple reviewers
internal class GitLabMergeRequestRequestReviewAction(
  private val scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
  private val projectDetailsLoader: GitLabProjectDetailsLoader,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestAction(CollaborationToolsBundle.message("review.details.action.request"), scope, reviewFlowVm) {
  override fun actionPerformed(event: ActionEvent) {
    val popupState: PopupState<JBPopup> = PopupState.forPopup()
    val parentComponent = event.source as? JComponent ?: return
    val point = RelativePoint.getSouthWestOf(parentComponent)
    scope.launch {
      val users = projectDetailsLoader.projectMembers()
        .filter { member -> isValidMergeRequestAccessLevel(member.accessLevel) }
        .map { member -> member.user }

      val selectedUser = ChooserPopupUtil.showChooserPopup(point, popupState, users) { user ->
        ChooserPopupUtil.PopupItemPresentation.Simple(shortText = user.username, icon = avatarIconsProvider.getIcon(user, AVATAR_SIZE))
      }

      // TODO: implement unselect
      if (selectedUser != null) {
        reviewFlowVm.setReviewers(listOf(selectedUser))
      }
    }
  }

  override fun enableCondition(): Boolean {
    return true // TODO: add condition
  }

  private fun isValidMergeRequestAccessLevel(accessLevel: GitLabAccessLevel): Boolean {
    return accessLevel == GitLabAccessLevel.REPORTER ||
           accessLevel == GitLabAccessLevel.DEVELOPER ||
           accessLevel == GitLabAccessLevel.MAINTAINER ||
           accessLevel == GitLabAccessLevel.OWNER
  }

  companion object {
    private const val AVATAR_SIZE = 20
  }
}

