// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.*
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.JComponent

internal object GitLabMergeRequestDiffInlayComponentsFactory {
  fun createDiscussion(project: Project,
                       cs: CoroutineScope,
                       avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                       vm: GitLabMergeRequestDiscussionViewModel,
                       place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent =
    GitLabDiscussionComponentFactory.create(project, cs, avatarIconsProvider, vm, place).apply {
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.getInlayPadding(CodeReviewChatItemUIUtil.ComponentType.COMPACT))
    }.let {
      CodeReviewCommentUIUtil.createEditorInlayPanel(it)
    }

  fun createNewDiscussion(project: Project,
                          cs: CoroutineScope,
                          avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                          vm: NewGitLabNoteViewModel,
                          onCancel: () -> Unit,
                          place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val addAction = vm.submitActionIn(cs, CollaborationToolsBundle.message("review.comment.submit"),
                                      project, NewGitLabNoteType.DIFF, place)
    val addAsDraftAction = vm.submitAsDraftActionIn(cs, CollaborationToolsBundle.message("review.comments.save-as-draft.action"),
                                                    project, NewGitLabNoteType.DIFF, place)

    val cancelAction = swingAction("") {
      if (vm.text.value.isBlank()) {
        onCancel()
      }
      else if (yesNo(CollaborationToolsBundle.message("review.comments.discard.new.confirmation.title"),
                     CollaborationToolsBundle.message("review.comments.discard.new.confirmation")).ask(project)) {
        onCancel()
      }
    }
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = vm.primarySubmitActionIn(cs, addAction, addAsDraftAction),
      secondaryActions = vm.secondarySubmitActionIn(cs, addAction, addAsDraftAction),
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = vm.submitActionHintIn(cs,
                                         CollaborationToolsBundle.message("review.comment.hint",
                                                                          CommentInputActionsComponentFactory.submitShortcutText),
                                         GitLabBundle.message("merge.request.details.action.draft.comment.hint",
                                                              CommentInputActionsComponentFactory.submitShortcutText))
    )

    val itemType = CodeReviewChatItemUIUtil.ComponentType.COMPACT
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, avatarIconsProvider, vm.currentUser)

    val editor = CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon).apply {
      border = JBUI.Borders.empty(itemType.inputPaddingInsets)
    }

    return CodeReviewCommentUIUtil.createEditorInlayPanel(editor)
  }
}