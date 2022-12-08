// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldModel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel

class GHCommentTextFieldFactory(private val model: CommentTextFieldModel) {

  fun create(actions: ActionsConfig, avatar: AvatarConfig? = null): JComponent {
    val textField = CommentTextFieldFactory.create(model)
    val inputConfig = CommentInputComponentFactory.Config(cancelConfig = actions.cancelAction?.let { CancelActionConfig(action = it) })

    val inputField = CommentInputComponentFactory.create(model, textField, inputConfig)
    val field = if (avatar == null) {
      inputField
    }
    else {
      wrapWithAvatar(inputField, avatar)
    }

    return CommentInputActionsComponentFactory.attachActions(field, actions.inputActions)
  }

  private fun wrapWithAvatar(component: JComponent, avatar: AvatarConfig): JComponent {
    val avatarSize = when (avatar.mode) {
      AvatarMode.COMMENT -> GHPRReviewCommentComponent.AVATAR_SIZE
      AvatarMode.TIMELINE -> GHPRTimelineItemUIUtil.MAIN_AVATAR_SIZE
    }
    val avatarGap = when (avatar.mode) {
      AvatarMode.COMMENT -> GHPRReviewCommentComponent.AVATAR_GAP
      AvatarMode.TIMELINE -> GHPRTimelineItemUIUtil.AVATAR_CONTENT_GAP
    }

    return CommentInputComponentFactory
      .addIconLeft(component,
                   avatar.avatarIconsProvider.getIcon(avatar.user.avatarUrl, avatarSize),
                   avatarGap,
                   avatar.user.getPresentableName())
  }

  data class AvatarConfig(val avatarIconsProvider: GHAvatarIconsProvider,
                          val user: GHUser,
                          val mode: AvatarMode = AvatarMode.COMMENT)

  enum class AvatarMode {
    COMMENT, TIMELINE
  }

  data class ActionsConfig(val inputActions: CommentInputActionsComponentFactory.Config,
                           val cancelAction: Action? = null)
}
