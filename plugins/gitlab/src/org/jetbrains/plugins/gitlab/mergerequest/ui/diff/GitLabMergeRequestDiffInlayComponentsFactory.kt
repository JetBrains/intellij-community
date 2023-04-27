// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.*
import javax.swing.JComponent

object GitLabMergeRequestDiffInlayComponentsFactory {
  fun createDiscussion(project: Project,
                       cs: CoroutineScope,
                       avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                       vm: GitLabDiscussionViewModel): JComponent =
    GitLabDiscussionComponentFactory.create(project, cs, avatarIconsProvider, vm).apply {
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.getInlayPadding(CodeReviewChatItemUIUtil.ComponentType.COMPACT))
    }.let {
      CodeReviewCommentUIUtil.createEditorInlayPanel(it)
    }

  fun createNewDiscussion(project: Project,
                          cs: CoroutineScope,
                          avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                          vm: NewGitLabNoteViewModel,
                          onCancel: () -> Unit): JComponent {
    val submitAction = swingAction(CollaborationToolsBundle.message("review.comment.submit")) {
      vm.submit()
    }.apply {
      bindEnabledIn(cs, vm.state.map { it != GitLabNoteEditingViewModel.SubmissionState.Loading })
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(submitAction),
      cancelAction = MutableStateFlow(swingAction("") { onCancel() }),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comment.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )


    val itemType = CodeReviewChatItemUIUtil.ComponentType.COMPACT
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, avatarIconsProvider, vm.currentUser)

    val editor = GitLabNoteEditorComponentFactory.create(project, cs, vm, actions, icon).apply {
      border = JBUI.Borders.empty(itemType.inputPaddingInsets)
    }

    return CodeReviewCommentUIUtil.createEditorInlayPanel(editor)
  }
}