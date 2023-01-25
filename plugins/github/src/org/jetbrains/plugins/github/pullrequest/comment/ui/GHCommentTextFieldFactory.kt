// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.wrapWithProgressOverlay
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory.create
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory.wrapWithLeftIcon
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

class GHCommentTextFieldFactory(private val model: GHCommentTextFieldModel) {

  fun create(inputActions: CommentInputActionsComponentFactory.Config, avatar: AvatarConfig? = null): JComponent {
    val textField = create(model.project, model.document)

    CollaborationToolsUIUtil.installValidator(textField, model.errorValue.map { it?.localizedMessage })
    val inputField = wrapWithProgressOverlay(textField, model.isBusyValue)

    val field = if (avatar == null) {
      inputField
    }
    else {
      wrapWithLeftIcon(avatar.componentType, inputField,
                       avatar.avatarIconsProvider, avatar.user.avatarUrl, avatar.user.getPresentableName())
    }

    return CommentInputActionsComponentFactory.attachActions(field, inputActions)
  }

  data class AvatarConfig(val avatarIconsProvider: GHAvatarIconsProvider,
                          val user: GHUser,
                          val componentType: CodeReviewChatItemUIUtil.ComponentType = CodeReviewChatItemUIUtil.ComponentType.COMPACT)
}
