// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel.NoteItem
import javax.swing.Action
import javax.swing.JComponent

object GitLabDiscussionComponentFactory {

  fun create(project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabDiscussionViewModel): JComponent {
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
        is NoteItem.Note -> GitLabNoteComponentFactory.create(ComponentType.COMPACT, project, itemCs, avatarIconsProvider, item.vm)
      }
    }

    return VerticalListPanel().apply {
      name = "GitLab Discussion Panel ${vm.id}"
      add(notesPanel)
      val replyVm = vm.replyVm
      if (replyVm != null) {
        bindChildIn(cs, replyVm.newNoteVm) { newNoteVm ->
          if (newNoteVm == null) {
            createReplyActionsPanel(replyVm, vm.resolveVm).apply {
              border = JBUI.Borders.empty(8, ComponentType.COMPACT.fullLeftShift)
            }
          }
          else {
            createReplyField(ComponentType.COMPACT, project, this, newNoteVm, vm.resolveVm, avatarIconsProvider, swingAction("") {
              replyVm.stopWriting()
            })
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
                       cancelAction: Action? = null): JComponent {
    val submitAction = swingAction(CollaborationToolsBundle.message("review.comments.reply.action")) {
      vm.submit()
    }.apply {
      bindEnabledIn(cs, vm.state.map { it != GitLabNoteEditingViewModel.SubmissionState.Loading })
    }

    val resolveAction = resolveVm?.takeIf { it.canResolve }?.let {
      swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
        resolveVm.changeResolvedState()
      }
    }?.apply {
      bindEnabledIn(cs, resolveVm.busy.inverted())
      bindTextIn(cs, resolveVm.actionTextFlow)
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(submitAction),
      additionalActions = MutableStateFlow(listOfNotNull(resolveAction)),
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )
    val icon = CommentTextFieldFactory.IconConfig.of(componentType, iconsProvider, vm.currentUser)

    return GitLabNoteEditorComponentFactory.create(project, cs, vm, actions, icon).let {
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(it, maxWidth = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + componentType.contentLeftShift)
    }.apply {
      border = JBUI.Borders.empty(componentType.inputPaddingInsets)
    }
  }

  private fun CoroutineScope.createReplyActionsPanel(replyVm: GitLabDiscussionReplyViewModel,
                                                     resolveVm: GitLabDiscussionResolveViewModel?): JComponent {
    val replyLink = ActionLink(CollaborationToolsBundle.message("review.comments.reply.action")) {
      replyVm.startWriting()
    }.apply {
      isFocusPainted = false
    }

    val cs = this
    return HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(replyLink)

      if (resolveVm != null && resolveVm.canResolve) {
        createUnResolveLink(cs, resolveVm).also(::add)
      }
    }
  }

  fun createUnResolveLink(cs: CoroutineScope, vm: GitLabDiscussionResolveViewModel): JComponent =
    ActionLink("") {
      vm.changeResolvedState()
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