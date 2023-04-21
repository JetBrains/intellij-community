// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.CommonBundle
import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.icon.OverlaidOffsetIconsIcon
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
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
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabDiscussionResolveViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createTitleTextPane
import org.jetbrains.plugins.gitlab.ui.comment.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@OptIn(ExperimentalCoroutinesApi::class)
object GitLabMergeRequestTimelineDiscussionComponentFactory {

  fun create(project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, vm).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibility(cs, vm.collapsed)
    }
    val mainNoteVm = vm.mainNote
    val textPanel = createNoteTextPanel(cs, mainNoteVm.flatMapLatest { it.htmlBody }).let {
      collapseDiscussionTextIfNeeded(cs, vm, it)
    }

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

    val repliesPanel = ComponentListPanelFactory.createVertical(cs, vm.replies, GitLabNoteViewModel::id) { noteCs, noteVm ->
      createNoteItem(project, noteCs, avatarIconsProvider, noteVm)
    }.apply {
      bindVisibility(cs, vm.collapsed.inverted())
    }

    val replyField = vm.newNoteVm?.let {
      createReplyField(project, cs, it, vm.resolveVm, avatarIconsProvider)
    }?.apply {
      bindVisibility(cs, vm.collapsed.inverted())
    }

    val titlePanel = HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(createTitleTextPane(cs, vm.author, vm.date))

      vm.resolveVm?.resolved?.let { resolvedFlow ->
        bindChild(cs, resolvedFlow) { _, resolved ->
          if (resolved) {
            CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag"))
          }
          else {
            null
          }
        }
      }
    }

    return CodeReviewChatItemUIUtil.buildDynamic(ComponentType.FULL,
                                                 { vm.author.createIconValue(cs, avatarIconsProvider, it) },
                                                 contentPanel) {
      withHeader(titlePanel, actionsPanel)
    }.let {
      VerticalListPanel().apply {
        add(it)
        add(repliesPanel)
        replyField?.let(::add)
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
                                        vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val authorsLabel = JLabel().apply {
      bindVisibility(cs, vm.replies.map { it.isNotEmpty() })

      val repliesAuthors = vm.replies.map { replies ->
        val authors = LinkedHashSet<GitLabUserDTO>()
        replies.mapTo(authors) { it.author }
      }

      bindIcon(cs, repliesAuthors.map { authors ->
        authors.map {
          avatarIconsProvider.getIcon(it, ComponentType.COMPACT.iconSize)
        }.nullize()?.let {
          OverlaidOffsetIconsIcon(it)
        }
      })
    }

    val hasRepliesOrCanCreateNewFlow = vm.replies.map { it.isNotEmpty() || vm.newNoteVm != null }

    val repliesLink = LinkLabel<Any>("", null, LinkListener { _, _ ->
      vm.setRepliesFolded(false)
    }).apply {
      bindVisibility(cs, hasRepliesOrCanCreateNewFlow)
      bindText(cs, vm.replies.map { replies ->
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
      bindVisibility(cs, vm.replies.map { it.isNotEmpty() })
      bindText(cs, vm.replies.mapNotNull { replies ->
        replies.lastOrNull()?.createdAt?.let { JBDateFormat.getFormatter().formatPrettyDateTime(it) }
      })
    }

    val repliesActions = HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GAP).apply {
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }.apply {
      bindVisibility(cs, hasRepliesOrCanCreateNewFlow)
    }
    return HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(repliesActions)

      vm.resolveVm?.let {
        createUnResolveLink(cs, it).also(::add)
      }
    }
  }

  private fun createUnResolveLink(cs: CoroutineScope,
                                  vm: GitLabDiscussionResolveViewModel): LinkLabel<Any> =
    LinkLabel<Any>("", null) { _, _ ->
      vm.changeResolvedState()
    }.apply {
      isFocusable = true
      bindDisabled(cs, vm.busy)
      bindText(cs, vm.actionTextFlow)
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
    return CodeReviewChatItemUIUtil.build(ComponentType.FULL_SECONDARY,
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

  private fun createReplyField(project: Project,
                               cs: CoroutineScope,
                               vm: NewGitLabNoteViewModel,
                               resolveVm: GitLabDiscussionResolveViewModel?,
                               iconsProvider: IconsProvider<GitLabUserDTO>): JComponent {
    val submitAction = swingAction(CollaborationToolsBundle.message("review.comments.reply.action")) {
      vm.submit()
    }.apply {
      bindEnabled(cs, vm.state.map { it != GitLabNoteEditingViewModel.SubmissionState.Loading })
    }

    val resolveAction = resolveVm?.let {
      swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
        resolveVm.changeResolvedState()
      }
    }?.apply {
      bindEnabled(cs, resolveVm.busy.inverted())
      bindText(cs, resolveVm.actionTextFlow)
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(submitAction),
      additionalActions = MutableStateFlow(listOfNotNull(resolveAction)),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )
    val itemType = ComponentType.FULL_SECONDARY
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, iconsProvider, vm.currentUser)

    return GitLabNoteEditorComponentFactory.create(project, cs, vm, actions, icon).let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, maxWidth = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = JBUI.Borders.empty(itemType.inputPaddingInsets)
    }
  }

  private val GitLabDiscussionResolveViewModel.actionTextFlow
    get() = resolved.map { resolved ->
      if (resolved) {
        CollaborationToolsBundle.message("review.comments.unresolve.action")
      }
      else {
        CollaborationToolsBundle.message("review.comments.resolve.action")
      }
    }

  private fun createNoteTextPanel(cs: CoroutineScope, textFlow: Flow<@Nls String>): JComponent =
    SimpleHtmlPane().apply {
      bindText(cs, textFlow)
    }

  private fun collapseDiscussionTextIfNeeded(cs: CoroutineScope, vm: GitLabMergeRequestTimelineDiscussionViewModel,
                                             textPane: JComponent): JComponent {
    val resolveVm = vm.resolveVm
    if (resolveVm == null) return textPane

    return JPanel(null).apply {
      name = "Text pane wrapper"
      isOpaque = false
      layout = SizeRestrictedSingleComponentLayout().apply {
        cs.launch {
          combine(vm.collapsed, resolveVm.resolved) { folded, resolved ->
            folded && resolved
          }.collect {
            if (it) {
              textPane.foreground = UIUtil.getContextHelpForeground()
              maxHeight = UIUtil.getUnscaledLineHeight(textPane) * 2
            }
            else {
              textPane.foreground = UIUtil.getLabelForeground()
              maxHeight = null
            }
            revalidate()
            repaint()
          }
        }
      }
      add(textPane)
    }
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