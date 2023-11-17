// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel.NoteItem
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.Action
import javax.swing.JComponent

internal object GitLabDiscussionComponentFactory {

  fun create(project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabMergeRequestDiscussionViewModel,
             place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val notesPanel = ComponentListPanelFactory.createVertical(cs, vm.notes, NoteItem::id) { itemCs, item ->
      when (item) {
        is NoteItem.Expander -> TimelineThreadCommentsPanel.createUnfoldComponent(item.collapsedCount) {
          item.expand()
        }.apply {
          border = JBUI.Borders.empty(TimelineThreadCommentsPanel.UNFOLD_BUTTON_VERTICAL_GAP,
                                      ComponentType.COMPACT.fullLeftShift,
                                      TimelineThreadCommentsPanel.UNFOLD_BUTTON_VERTICAL_GAP,
                                      0)
        }
        is NoteItem.Note -> GitLabNoteComponentFactory.create(ComponentType.COMPACT, project, itemCs, avatarIconsProvider, item.vm, place)
      }
    }

    return VerticalListPanel().apply {
      name = "GitLab Discussion Panel ${vm.id}"
      add(notesPanel)

      cs.launchNow {
        vm.replyVm.collectLatest { replyVm ->
          if (replyVm == null) return@collectLatest

          coroutineScope {
            bindChildIn(this, replyVm.newNoteVm) { newNoteVm ->
              newNoteVm?.let {
                createReplyField(ComponentType.COMPACT, project, this, newNoteVm, vm.resolveVm, avatarIconsProvider, place,
                                 swingAction("") { replyVm.stopWriting() })
              } ?: createReplyActionsPanel(replyVm, vm.resolveVm, project, place).apply {
                border = JBUI.Borders.empty(8, ComponentType.COMPACT.fullLeftShift)
              }
            }
          }
        }
      }
    }
  }

  fun createReplyField(componentType: ComponentType,
                       project: Project,
                       cs: CoroutineScope,
                       vm: NewGitLabNoteViewModel,
                       resolveVm: GitLabDiscussionResolveViewModel?,
                       iconsProvider: IconsProvider<GitLabUserDTO>,
                       place: GitLabStatistics.MergeRequestNoteActionPlace,
                       cancelAction: Action? = null): JComponent {
    val resolveAction = resolveVm?.takeIf { it.canResolve }?.let {
      swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
        resolveVm.changeResolvedState()
        GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CHANGE_DISCUSSION_RESOLVE, place)
      }
    }?.apply {
      bindEnabledIn(cs, resolveVm.busy.inverted())
      bindTextIn(cs, resolveVm.actionTextFlow)
    }

    val addAction = vm.submitActionIn(cs, CollaborationToolsBundle.message("review.comment.submit"),
                                      project, NewGitLabNoteType.REPLY, place)
    val addAsDraftAction = vm.submitAsDraftActionIn(cs, CollaborationToolsBundle.message("review.comments.save-as-draft.action"),
                                                    project, NewGitLabNoteType.REPLY, place)

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = vm.primarySubmitActionIn(cs, addAction, addAsDraftAction),
      secondaryActions = vm.secondarySubmitActionIn(cs, addAction, addAsDraftAction),
      additionalActions = MutableStateFlow(listOfNotNull(resolveAction)),
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = vm.submitActionHintIn(cs,
                                         CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                          CommentInputActionsComponentFactory.submitShortcutText),
                                         GitLabBundle.message("merge.request.details.action.draft.reply.hint",
                                                              CommentInputActionsComponentFactory.submitShortcutText)
      )
    )
    val icon = CommentTextFieldFactory.IconConfig.of(componentType, iconsProvider, vm.currentUser)

    return CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon).let {
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(it, maxWidth = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + componentType.contentLeftShift)
    }.apply {
      border = JBUI.Borders.empty(componentType.inputPaddingInsets)
    }
  }

  private fun CoroutineScope.createReplyActionsPanel(replyVm: GitLabDiscussionReplyViewModel,
                                                     resolveVm: GitLabDiscussionResolveViewModel?,
                                                     project: Project,
                                                     place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val replyLink = ActionLink(CollaborationToolsBundle.message("review.comments.reply.action")) {
      replyVm.startWriting()
    }.apply {
      isFocusPainted = false
    }

    val cs = this
    return HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(replyLink)

      if (resolveVm != null && resolveVm.canResolve) {
        createUnResolveLink(cs, resolveVm, project, place).also(::add)
      }
    }
  }

  fun createUnResolveLink(cs: CoroutineScope, vm: GitLabDiscussionResolveViewModel,
                          project: Project, place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent =
    ActionLink("") {
      vm.changeResolvedState()
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CHANGE_DISCUSSION_RESOLVE, place)
    }.apply {
      autoHideOnDisable = false
      isFocusPainted = false
      bindDisabledIn(cs, vm.busy)
      bindTextIn(cs, vm.actionTextFlow)
    }
}

val GitLabDiscussionResolveViewModel.actionTextFlow
  get() = resolved.map { resolved ->
    if (resolved) {
      CollaborationToolsBundle.message("review.comments.unresolve.action")
    }
    else {
      CollaborationToolsBundle.message("review.comments.resolve.action")
    }
  }