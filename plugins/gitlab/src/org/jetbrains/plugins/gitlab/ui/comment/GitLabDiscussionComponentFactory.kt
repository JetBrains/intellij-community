// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
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
    val notesPanel = ComponentListPanelFactory.createVertical(cs, vm.notes) { item ->
      val itemCs = this
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
                createReplyField(ComponentType.COMPACT, project, this, vm, newNoteVm, avatarIconsProvider, place,
                                 swingAction("") { replyVm.stopWriting() })
              } ?: createReplyActionsPanel(vm, replyVm, project, place).apply {
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
                       vm: CodeReviewResolvableItemViewModel,
                       editVm: NewGitLabNoteViewModel,
                       iconsProvider: IconsProvider<GitLabUserDTO>,
                       place: GitLabStatistics.MergeRequestNoteActionPlace,
                       cancelAction: Action? = null): JComponent {
    val additionalActions = vm.canChangeResolvedState.mapScoped {
      listOf(createResolveAction(project, vm, place))
    }.stateIn(cs, SharingStarted.Eagerly, emptyList())

    val addAction = editVm.submitActionIn(cs, CollaborationToolsBundle.message("review.comment.submit"),
                                          project, NewGitLabNoteType.REPLY, place)
    val addAsDraftAction = editVm.submitAsDraftActionIn(cs, CollaborationToolsBundle.message("review.comments.save-as-draft.action"),
                                                        project, NewGitLabNoteType.REPLY, place)

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = editVm.primarySubmitActionIn(cs, addAction, addAsDraftAction),
      secondaryActions = editVm.secondarySubmitActionIn(cs, addAction, addAsDraftAction),
      additionalActions = additionalActions,
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = editVm.submitActionHintIn(cs,
                                         CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                          CommentInputActionsComponentFactory.submitShortcutText),
                                         GitLabBundle.message("merge.request.details.action.draft.reply.hint",
                                                              CommentInputActionsComponentFactory.submitShortcutText)
      )
    )
    val icon = CommentTextFieldFactory.IconConfig.of(componentType, iconsProvider, editVm.currentUser)

    return CodeReviewCommentTextFieldFactory.createIn(cs, editVm, actions, icon).let {
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(it, maxWidth = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + componentType.contentLeftShift)
    }.apply {
      border = JBUI.Borders.empty(componentType.inputPaddingInsets)
    }
  }

  private fun CoroutineScope.createReplyActionsPanel(vm: GitLabMergeRequestDiscussionViewModel,
                                                     replyVm: GitLabDiscussionReplyViewModel,
                                                     project: Project,
                                                     place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val cs = this
    val replyLink = ActionLink(CollaborationToolsBundle.message("review.comments.reply.action")) {
      replyVm.startWriting()
    }.apply {
      isFocusPainted = false
    }

    val resolveLink = ActionLink(createResolveAction(project, vm, place)).apply {
      autoHideOnDisable = false
      bindVisibilityIn(cs, vm.canChangeResolvedState)
    }

    return HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(replyLink)
      add(resolveLink)
    }
  }
}

private fun CoroutineScope.createResolveAction(project: Project,
                                               resolveVm: CodeReviewResolvableItemViewModel,
                                               place: GitLabStatistics.MergeRequestNoteActionPlace) =
  swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
    resolveVm.changeResolvedState()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CHANGE_DISCUSSION_RESOLVE, place)
  }.apply {
    bindEnabledIn(this@createResolveAction, resolveVm.isBusy.inverted())
    bindTextIn(this@createResolveAction, resolveVm.isResolved.map(CodeReviewCommentUIUtil::getResolveToggleActionText))
  }