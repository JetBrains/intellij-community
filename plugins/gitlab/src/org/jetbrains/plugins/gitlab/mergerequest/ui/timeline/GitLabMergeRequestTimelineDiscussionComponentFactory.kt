// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.CommonBundle
import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.icon.OverlaidOffsetIconsIcon
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.containers.nullize
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createTitleTextPane
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditorComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

@OptIn(ExperimentalCoroutinesApi::class)
object GitLabMergeRequestTimelineDiscussionComponentFactory {

  fun create(project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             discussion: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, discussion).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibility(cs, discussion.repliesFolded)
    }
    val mainNoteVm = discussion.mainNote
    val textPanel = createNoteTextPanel(cs, mainNoteVm.flatMapLatest { it.htmlBody })

    // oh well... probably better to make a suitable API in EditableComponentFactory, but that would look ugly
    val actionAndEditVmsFlow: Flow<Pair<GitLabNoteAdminActionsViewModel, GitLabNoteEditingViewModel>?> =
      mainNoteVm.flatMapLatest { note ->
        val actionsVm = note.actionsVm
        actionsVm?.editVm?.map { it?.let { actionsVm to it } } ?: flowOf(null)
      }

    val textContentPanel = EditableComponentFactory.create(cs, textPanel, actionAndEditVmsFlow) { editCs, (actionsVm, editVm) ->
      GitLabNoteEditorComponentFactory.create(project, editCs, editVm, createEditNoteActions(actionsVm, editVm))
    }

    val contentPanel = VerticalListPanel().apply {
      add(textContentPanel)
      add(repliesActionsPanel)
    }

    val actionsPanel = createNoteActions(cs, mainNoteVm)

    val repliesPanel = ComponentListPanelFactory.createVertical(cs, discussion.replies, GitLabNoteViewModel::id, 0) { noteCs, noteVm ->
      createNoteItem(project, noteCs, avatarIconsProvider, noteVm)
    }.apply {
      bindVisibility(cs, discussion.repliesFolded.inverted())
    }

    return CodeReviewChatItemUIUtil.buildDynamic(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                                 { discussion.author.createIconValue(cs, avatarIconsProvider, it) },
                                                 contentPanel) {
      withHeader(createTitleTextPane(cs, discussion.author, discussion.date), actionsPanel)
    }.let {
      VerticalListPanel().apply {
        add(it)
        add(repliesPanel)
      }
    }
  }

  private fun Flow<GitLabUserDTO>.createIconValue(cs: CoroutineScope, iconsProvider: IconsProvider<GitLabUserDTO>, size: Int) =
    SingleValueModel<Icon>(EmptyIcon.create(size)).apply {
      cs.launch {
        collect {
          value = iconsProvider.getIcon(it, size)
        }
      }
    }

  private fun createRepliesActionsPanel(cs: CoroutineScope,
                                        avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                        item: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val authorsLabel = JLabel().apply {
      bindVisibility(cs, item.replies.map { it.isNotEmpty() })

      val repliesAuthors = item.replies.map { replies ->
        val authors = LinkedHashSet<GitLabUserDTO>()
        replies.mapTo(authors) { it.author }
      }

      bindIcon(cs, repliesAuthors.map { authors ->
        authors.map {
          avatarIconsProvider.getIcon(it, CodeReviewChatItemUIUtil.ComponentType.COMPACT.iconSize)
        }.nullize()?.let {
          OverlaidOffsetIconsIcon(it)
        }
      })
    }

    val repliesLink = LinkLabel<Any>("", null, LinkListener { _, _ ->
      item.setRepliesFolded(false)
    }).apply {
      bindVisibility(cs, item.replies.map { it.isNotEmpty() })
      bindText(cs, item.replies.map { replies ->
        val replyCount = replies.size
        if (replyCount == 0) {
          CollaborationToolsBundle.message("review.comments.reply.action")
        }
        else {
          CollaborationToolsBundle.message("review.comments.replies.action", replyCount)
        }
      })
    }

    val lastReplyDateLabel = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
    }.apply {
      bindVisibility(cs, item.replies.map { it.isNotEmpty() })
      bindText(cs, item.replies.mapNotNull { replies ->
        replies.lastOrNull()?.createdAt?.let { JBDateFormat.getFormatter().formatPrettyDateTime(it) }
      })
    }

    val repliesActions = HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GAP).apply {
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }.apply {
      bindVisibility(cs, item.replies.map { it.isNotEmpty() })
    }
    return HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(repliesActions)

      item.resolvedVm?.let {
        createUnResolveLink(cs, it).also(::add)
      }
    }
  }

  private fun createUnResolveLink(cs: CoroutineScope,
                                  vm: GitLabMergeRequestDiscussionResolveViewModel): LinkLabel<Any> =
    LinkLabel<Any>("", null) { _, _ ->
      vm.changeResolvedState()
    }.apply {
      isFocusable = true
      bindDisabled(cs, vm.busy)
      bindText(cs, vm.resolved.map { resolved ->
        if (resolved) {
          CollaborationToolsBundle.message("review.comments.unresolve.action")
        }
        else {
          CollaborationToolsBundle.message("review.comments.resolve.action")
        }
      })
    }

  private fun createNoteItem(project: Project,
                             cs: CoroutineScope,
                             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                             vm: GitLabNoteViewModel): JComponent {
    val textPanel = createNoteTextPanel(cs, vm.htmlBody)

    val actionsVm = vm.actionsVm
    val contentPanel = if (actionsVm != null) {
      EditableComponentFactory.create(cs, textPanel, actionsVm.editVm) { editCs, editVm ->
        GitLabNoteEditorComponentFactory.create(project, editCs, editVm, createEditNoteActions(actionsVm, editVm))
      }
    }
    else {
      textPanel
    }

    val actionsPanel = createNoteActions(cs, flowOf(vm))
    return CodeReviewChatItemUIUtil.build(CodeReviewChatItemUIUtil.ComponentType.FULL_SECONDARY,
                                          { avatarIconsProvider.getIcon(vm.author, it) },
                                          contentPanel) {
      withHeader(createTitleTextPane(vm.author, vm.createdAt), actionsPanel)
    }
  }

  private fun createNoteActions(cs: CoroutineScope, note: Flow<GitLabNoteViewModel>): JComponent {
    val panel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      cs.launch {
        note.mapNotNull { it.actionsVm }.collectLatest {
          removeAll()
          coroutineScope {
            CodeReviewCommentUIUtil.createEditButton { _ -> it.startEditing() }.apply {
              bindDisabled(this@coroutineScope, it.busy)
            }.also(::add)
            CodeReviewCommentUIUtil.createDeleteCommentIconButton { _ -> it.delete() }.apply {
              bindDisabled(this@coroutineScope, it.busy)
            }.also(::add)
            repaint()
            revalidate()
            awaitCancellation()
          }
        }
      }
    }
    return panel
  }

  private fun createNoteTextPanel(cs: CoroutineScope, textFlow: Flow<@Nls String>): JComponent =
    SimpleHtmlPane().apply {
      bindText(cs, textFlow)
    }

  private fun createEditNoteActions(actionsVm: GitLabNoteAdminActionsViewModel,
                                    editVm: GitLabNoteEditingViewModel) =
    CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(swingAction(CollaborationToolsBundle.message("review.comment.save")) {
        editVm.submit()
      }),
      cancelAction = MutableStateFlow(swingAction(CommonBundle.getCancelButtonText()) {
        actionsVm.stopEditing()
      }),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comment.save.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )
}