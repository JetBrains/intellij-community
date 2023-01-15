// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.icon.OverlaidOffsetIconsIcon
import com.intellij.collaboration.ui.util.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.containers.nullize
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestNoteViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createTitleTextPane
import javax.swing.JComponent
import javax.swing.JLabel

@OptIn(FlowPreview::class)
object GitLabMergeRequestTimelineDiscussionComponentFactory {

  suspend fun create(cs: CoroutineScope,
                     avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                     item: GitLabMergeRequestTimelineItemViewModel.Discussion): JComponent {
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, item).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibility(cs, item.repliesFolded)
    }
    val contentPanel = createNoteTextPanel(cs, item.mainNote.map { it.htmlBody }.flattenConcat()).let {
      VerticalListPanel().apply {
        add(it)
        add(repliesActionsPanel)
      }
    }

    val repliesPanel = VerticalListPanel().apply {
      cs.launch {
        item.replies.combine(item.repliesFolded) { replies, folded ->
          if (!folded) replies else emptyList()
        }.collectLatest { notes ->
          coroutineScope {
            val notesScope = this
            removeAll()
            notes.forEach {
              add(createNoteItem(notesScope, avatarIconsProvider, it))
            }
            revalidate()
            repaint()
            awaitCancellation()
          }
        }
      }
      bindVisibility(cs, item.repliesFolded.inverted())
    }

    //TODO: dynamic icon
    val constAuthor = item.author.first()
    return CodeReviewChatItemUIUtil.build(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                          { avatarIconsProvider.getIcon(constAuthor, it) },
                                          contentPanel) {
      withHeader(createTitleTextPane(cs, item.author, item.date))
    }.let {
      VerticalListPanel().apply {
        add(it)
        add(repliesPanel)
      }
    }
  }

  private fun createRepliesActionsPanel(cs: CoroutineScope,
                                        avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                        item: GitLabMergeRequestTimelineItemViewModel.Discussion): JComponent {
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

  private fun createNoteItem(cs: CoroutineScope,
                             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                             vm: GitLabMergeRequestNoteViewModel): JComponent {
    val contentPanel = createNoteTextPanel(cs, vm.htmlBody)
    return CodeReviewChatItemUIUtil.build(CodeReviewChatItemUIUtil.ComponentType.FULL_SECONDARY,
                                          { avatarIconsProvider.getIcon(vm.author, it) },
                                          contentPanel) {
      withHeader(createTitleTextPane(vm.author, vm.createdAt))
    }
  }

  private fun createNoteTextPanel(cs: CoroutineScope, textFlow: Flow<@Nls String>): JComponent =
    SimpleHtmlPane().apply {
      bindText(cs, textFlow)
    }
}