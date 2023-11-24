// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded
import com.intellij.collaboration.ui.codereview.ToggleableContainer
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.CollapsibleTimelineItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.diff.util.LineRange
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.OverlaidOffsetIconsIcon
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.containers.nullize
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRSelectInToolWindowHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.cloneDialog.GHCloneDialogExtensionComponentBase.Companion.items
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object GHPRReviewThreadComponent {

  val INLAY_COMPONENT_TYPE = CodeReviewChatItemUIUtil.ComponentType.COMPACT

  fun createForInlay(project: Project,
                     thread: GHPRReviewThreadModel,
                     reviewDataProvider: GHPRReviewDataProvider,
                     htmlImageLoader: AsyncHtmlImageLoader,
                     avatarIconsProvider: GHAvatarIconsProvider,
                     suggestedChangeHelper: GHPRSuggestedChangeHelper,
                     ghostUser: GHUser,
                     currentUser: GHUser): JComponent {
    val panel = VerticalListPanel()
    val commentComponentFactory = GHPRReviewCommentComponent.factory(project, thread, ghostUser,
                                                                     reviewDataProvider,
                                                                     htmlImageLoader,
                                                                     avatarIconsProvider,
                                                                     suggestedChangeHelper,
                                                                     INLAY_COMPONENT_TYPE)
    val commentsPanel = TimelineThreadCommentsPanel(thread, commentComponentFactory, 0, INLAY_COMPONENT_TYPE.fullLeftShift)
    panel.add(commentsPanel)

    if (reviewDataProvider.canComment()) {
      panel.add(getThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser).apply {
        border = JBUI.Borders.empty(INLAY_COMPONENT_TYPE.inputPaddingInsets)
      })
    }
    return panel
  }

  fun createThreadDiffIn(cs: CoroutineScope,
                         project: Project,
                         thread: GHPRReviewThreadModel,
                         selectInToolWindowHelper: GHPRSelectInToolWindowHelper): JComponent {
    val vm = object : CollapsibleTimelineItemViewModel {
      override val collapsible = MutableStateFlow(false)

      init {
        thread.addAndInvokeStateChangeListener {
          collapsible.value = thread.isResolved || thread.isOutdated
        }
      }

      override val collapsed: Flow<Boolean> =
        combine(thread.collapsedState, collapsible) { collapsed, collapsible ->
          if (collapsible) collapsed else false
        }.distinctUntilChanged()

      override fun setCollapsed(collapsed: Boolean) {
        thread.collapsedState.value = collapsed
      }
    }

    val fileNameClickListener = flowOf(ActionListener {
      val commit = if (thread.isOutdated || thread.commit?.oid == null) {
        thread.originalCommit?.oid
      }
      else {
        null
      }
      selectInToolWindowHelper.selectChange(commit, thread.filePath)
    })
    return TimelineDiffComponentFactory.createDiffWithHeader(cs, vm, thread.filePath, fileNameClickListener) {
      createDiff(thread, project)
    }
  }

  private fun CoroutineScope.createDiff(thread: GHPRReviewThreadModel, project: Project): JComponent {
    val hunk = thread.patchHunk
    if (hunk == null || hunk.lines.isEmpty()) {
      return JLabel(CollaborationToolsBundle.message("review.thread.diff.not.loaded"))
    }

    val anchorLocation = thread.originalLocation
    val startAnchorLocation = thread.originalStartLocation

    val anchorLength = if (startAnchorLocation?.first == anchorLocation?.first) {
      ((anchorLocation?.second ?: 0) - (startAnchorLocation?.second ?: 0)).coerceAtLeast(0)
    }
    else {
      0
    }

    val hunkLength = anchorLength + TimelineDiffComponentFactory.DIFF_CONTEXT_SIZE
    val truncatedHunk = PatchHunkUtil.truncateHunkBefore(hunk, hunk.lines.lastIndex - hunkLength)

    val anchorRange = LineRange(truncatedHunk.lines.lastIndex - anchorLength, truncatedHunk.lines.size)
    return TimelineDiffComponentFactory
      .createDiffComponentIn(this, project, EditorFactory.getInstance(), truncatedHunk, anchorRange)
  }

  private fun getThreadActionsComponent(
    project: Project,
    reviewDataProvider: GHPRReviewDataProvider,
    thread: GHPRReviewThreadModel,
    avatarIconsProvider: GHAvatarIconsProvider,
    currentUser: GHUser
  ): JComponent {
    val toggleModel = SingleValueModel(false)

    return ToggleableContainer.create(
      toggleModel,
      {
        createCollapsedThreadActionsComponent(reviewDataProvider, thread) {
          toggleModel.value = true
        }
      },
      {
        createUncollapsedThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser) {
          toggleModel.value = false
        }
      }
    )
  }

  private fun createCollapsedThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                                    thread: GHPRReviewThreadModel,
                                                    onReply: () -> Unit): JComponent {

    val toggleReplyLink = LinkLabel<Any>(CollaborationToolsBundle.message("review.comments.reply.action"), null) { _, _ ->
      onReply()
    }.apply {
      isFocusable = true
    }

    val unResolveLink = createUnResolveLink(reviewDataProvider, thread)

    return HorizontalListPanel(ActionsFolded.HORIZONTAL_GAP).apply {
      border = JBUI.Borders.emptyLeft(INLAY_COMPONENT_TYPE.contentLeftShift)

      add(toggleReplyLink)
      add(unResolveLink)
    }
  }

  fun createUncollapsedThreadActionsComponent(project: Project, reviewDataProvider: GHPRReviewDataProvider,
                                              thread: GHPRReviewThreadModel,
                                              avatarIconsProvider: GHAvatarIconsProvider,
                                              currentUser: GHUser,
                                              onDone: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) { text ->
      reviewDataProvider.addComment(EmptyProgressIndicator(), thread.getElementAt(0).id, text).successOnEdt {
        thread.addComment(it)
        onDone()
      }
    }

    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText

    val unResolveAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        textFieldModel.isBusyValue.value = true
        if (thread.isResolved) {
          reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
        }
        else {
          reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
        }.handleOnEdt { _, _ ->
          textFieldModel.isBusyValue.value = false
        }
      }
    }

    thread.addAndInvokeStateChangeListener {
      val name = if (thread.isResolved) {
        CollaborationToolsBundle.message("review.comments.unresolve.action")
      }
      else {
        CollaborationToolsBundle.message("review.comments.resolve.action")
      }
      unResolveAction.putValue(Action.NAME, name)
    }

    textFieldModel.isBusyValue.addAndInvokeListener {
      unResolveAction.isEnabled = !it
    }

    val cancelAction = swingAction("") {
      onDone()
    }
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(textFieldModel.submitAction(CollaborationToolsBundle.message("review.comments.reply.action"))),
      cancelAction = MutableStateFlow(cancelAction),
      additionalActions = MutableStateFlow(listOf(unResolveAction)),
      submitHint = MutableStateFlow(GithubBundle.message("pull.request.review.thread.reply.hint", submitShortcutText))
    )
    val icon = CommentTextFieldFactory.IconConfig.of(
      CodeReviewChatItemUIUtil.ComponentType.COMPACT, avatarIconsProvider, currentUser.avatarUrl
    )

    return GHCommentTextFieldFactory(textFieldModel).create(actions, icon)
  }

  fun getCollapsedThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                         avatarIconsProvider: GHAvatarIconsProvider,
                                         thread: GHPRReviewThreadModel,
                                         ghostUser: GHUser,
                                         onReply: () -> Unit): JComponent {
    val authorsLabel = JLabel()
    val repliesLink = LinkLabel<Any>("", null, LinkListener { _, _ ->
      onReply()
    })
    val lastReplyDateLabel = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
    }

    val repliesModel = thread.repliesModel
    repliesModel.addListDataListener(object : ListDataListener {
      init {
        update()
      }

      private fun update() {
        val authors = LinkedHashSet<GHActor>()
        val repliesCount = repliesModel.size

        repliesModel.items.mapTo(authors) {
          it.author ?: ghostUser
        }

        authorsLabel.apply {
          icon = authors.map { avatarIconsProvider.getIcon(it.avatarUrl, Avatar.Sizes.BASE) }.nullize()?.let {
            OverlaidOffsetIconsIcon(it)
          }
          isVisible = icon != null
        }

        repliesLink.apply {
          text = if (repliesCount == 0) {
            CollaborationToolsBundle.message("review.comments.reply.action")
          }
          else {
            CollaborationToolsBundle.message("review.comments.replies.action", repliesCount)
          }
          isVisible = reviewDataProvider.canComment() || repliesCount > 0
        }

        lastReplyDateLabel.apply {
          isVisible = repliesCount > 0
          if (isVisible) {
            text = repliesModel.getElementAt(repliesModel.size - 1).dateCreated.let {
              DateFormatUtil.formatPrettyDateTime(it)
            }
          }
        }
      }

      override fun intervalAdded(e: ListDataEvent) = update()
      override fun intervalRemoved(e: ListDataEvent) = update()
      override fun contentsChanged(e: ListDataEvent) = Unit
    })

    val repliesPanel = HorizontalListPanel(ActionsFolded.HORIZONTAL_GAP).apply {
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }

    val unResolveLink = createUnResolveLink(reviewDataProvider, thread)

    return HorizontalListPanel(ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(repliesPanel)
      unResolveLink?.also(::add)
    }
  }

  private fun createUnResolveLink(reviewDataProvider: GHPRReviewDataProvider, thread: GHPRReviewThreadModel): LinkLabel<Any>? {
    if (!reviewDataProvider.canComment()) return null
    val unResolveLink = LinkLabel<Any>("", null) { comp, _ ->
      comp.isEnabled = false
      if (thread.isResolved) {
        reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
      }
      else {
        reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
      }.handleOnEdt { _, _ ->
        comp.isEnabled = true
      }
    }.apply {
      isFocusable = true
    }

    thread.addAndInvokeStateChangeListener {
      unResolveLink.text = if (thread.isResolved) {
        CollaborationToolsBundle.message("review.comments.unresolve.action")
      }
      else {
        CollaborationToolsBundle.message("review.comments.resolve.action")
      }
    }
    return unResolveLink
  }
}
