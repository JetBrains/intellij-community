// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.ui.comment.GitLabCodeReviewCommentTextFieldFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteType
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.primarySubmitActionIn
import org.jetbrains.plugins.gitlab.ui.comment.secondarySubmitActionIn
import org.jetbrains.plugins.gitlab.ui.comment.submitActionHintIn
import org.jetbrains.plugins.gitlab.ui.comment.submitActionIn
import org.jetbrains.plugins.gitlab.ui.comment.submitAsDraftActionIn
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.JComponent

internal object GitLabMergeRequestDiffInlayComponentsFactory {
  private const val VERTICAL_INLAY_MARGIN = 8
  private const val LEFT_INLAY_MARGIN = 34
  private const val RIGHT_INLAY_MARGIN = 0

  fun createDiscussion(
    project: Project,
    cs: CoroutineScope,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    imageLoader: GitLabImageLoader,
    vm: GitLabMergeRequestDiscussionViewModel,
    place: GitLabStatistics.MergeRequestNoteActionPlace,
  ): JComponent =
    GitLabDiscussionComponentFactory.create(project, cs, avatarIconsProvider, imageLoader, vm, place).apply {
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.getInlayPadding(CodeReviewChatItemUIUtil.ComponentType.COMPACT))
    }.let {
      wrapInRoundedPanel(it, vm)
    }

  fun createDraftNote(
    project: Project,
    cs: CoroutineScope,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    imageLoader: GitLabImageLoader,
    vm: GitLabNoteViewModel,
    place: GitLabStatistics.MergeRequestNoteActionPlace,
  ): JComponent =
    GitLabNoteComponentFactory.create(CodeReviewChatItemUIUtil.ComponentType.COMPACT, project, cs, avatarIconsProvider,
                                      imageLoader, vm, place).apply {
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.getInlayPadding(CodeReviewChatItemUIUtil.ComponentType.COMPACT))
    }.let {
      wrapInRoundedPanel(it, vm)
    }

  private fun wrapInRoundedPanel(component: JComponent, vm: FocusableViewModel): JComponent =
    CodeReviewCommentUIUtil.createEditorInlayPanel(component).apply {
      isFocusable = true
      launchOnShow("focusRequests") {
        vm.focusRequests.collect { requestFocus(false) }
      }
    }.let { roundedPanel ->
      if (AdvancedSettings.getBoolean("show.review.threads.with.increased.margins")) {
        Wrapper(roundedPanel).apply {
          border = JBUI.Borders.empty(VERTICAL_INLAY_MARGIN, LEFT_INLAY_MARGIN, VERTICAL_INLAY_MARGIN, RIGHT_INLAY_MARGIN)
        }
      }
      else {
        roundedPanel
      }
    }

  fun createNewDiscussion(
    project: Project,
    cs: CoroutineScope,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    vm: NewGitLabNoteViewModel,
    onCancel: () -> Unit,
    place: GitLabStatistics.MergeRequestNoteActionPlace,
  ): JComponent {
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

    val editor = GitLabCodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon).apply {
      border = JBUI.Borders.empty(itemType.inputPaddingInsets)
    }

    return if (AdvancedSettings.getBoolean("show.review.threads.with.increased.margins")) {
      Wrapper(CodeReviewCommentUIUtil.createEditorInlayPanel(editor)).apply {
        border = JBUI.Borders.empty(VERTICAL_INLAY_MARGIN, LEFT_INLAY_MARGIN, VERTICAL_INLAY_MARGIN, RIGHT_INLAY_MARGIN)
      }
    }
    else {
      CodeReviewCommentUIUtil.createEditorInlayPanel(editor)
    }
  }
}