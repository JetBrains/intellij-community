// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldModel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.Action
import javax.swing.JComponent

class GHCommentTextFieldFactory(private val model: CommentTextFieldModel) {

  fun create(actions: ActionsConfig, avatar: AvatarConfig? = null): JComponent {
    val textField = CommentTextFieldFactory.create(model)
    val inputConfig = CommentInputComponentFactory.Config(cancelConfig = actions.cancelAction?.let { CancelActionConfig(action = it) })

    val inputField = CommentInputComponentFactory.create(model, textField, inputConfig)
    val field = if (avatar == null) {
      inputField
    }
    else {
      CommentInputComponentFactory
        .addIconLeft(avatar.componentType, inputField,
                     avatar.avatarIconsProvider, avatar.user.avatarUrl, avatar.user.getPresentableName())
    }

    return CommentInputActionsComponentFactory.attachActions(field, actions.inputActions)
  }

  data class AvatarConfig(val avatarIconsProvider: GHAvatarIconsProvider,
                          val user: GHUser,
                          val componentType: CodeReviewChatItemUIUtil.ComponentType = CodeReviewChatItemUIUtil.ComponentType.COMPACT)

  data class ActionsConfig(val inputActions: CommentInputActionsComponentFactory.Config,
                           val cancelAction: Action? = null)
}
