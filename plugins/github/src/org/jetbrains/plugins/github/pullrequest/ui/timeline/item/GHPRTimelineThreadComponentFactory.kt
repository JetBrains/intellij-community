// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.launchNowIn
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.buildTimelineItem
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel

internal object GHPRTimelineThreadComponentFactory {
  fun createIn(cs: CoroutineScope,
               vm: GHPRTimelineThreadViewModel): JComponent =
    VerticalListPanel().apply {
      name = "GitHub Thread Panel ${vm.id}"
      add(cs.createThreadItem(vm))
      add(cs.createRepliesPanel(vm))
    }

  private fun CoroutineScope.createThreadItem(vm: GHPRTimelineThreadViewModel): JComponent =
    buildTimelineItem(vm.avatarIconsProvider, vm.author, createContent(vm)) {
      maxContentWidth = null
      val titlePanel = createTitlePanel(vm)
      val actionsPanel = createThreadActions(vm)
      withHeader(titlePanel, actionsPanel)
    }

  private fun CoroutineScope.createTitlePanel(vm: GHPRTimelineThreadViewModel): JComponent {
    val textPanel = CodeReviewTimelineUIUtil.createTitleTextPane(vm.author.getPresentableName(), vm.author.url, vm.createdAt)
    val tagsPanel = createTagsPanel(vm)
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(textPanel)
      add(tagsPanel)
    }
  }

  private fun CoroutineScope.createTagsPanel(vm: GHPRTimelineThreadViewModel): JComponent {
    val cs = this
    val outdatedLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.outdated.tag")).apply {
      isVisible = false
      bindVisibilityIn(cs, vm.isOutdated)
    }
    val resolvedLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag")).apply {
      isVisible = false
      bindVisibilityIn(cs, vm.isResolved)
    }
    val pendingLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.pending.tag")).apply {
      isVisible = false
      bindVisibilityIn(cs, vm.isPending)
    }
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(outdatedLabel)
      add(resolvedLabel)
      add(pendingLabel)
    }
  }

  private fun CoroutineScope.createThreadActions(vm: GHPRTimelineThreadViewModel): JComponent =
    HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      launchNow {
        vm.mainCommentVm.collectLatest {
          removeAll()
          val commentVm = it ?: return@collectLatest
          supervisorScope {
            if (commentVm.canEdit) {
              add(CodeReviewCommentUIUtil.createEditButton {
                commentVm.editBody()
              }.apply {
                bindDisabledIn(this@supervisorScope, commentVm.isBusy)
              })
            }
            if (commentVm.canDelete) {
              add(CodeReviewCommentUIUtil.createDeleteCommentIconButton {
                commentVm.delete()
              }.apply {
                bindDisabledIn(this@supervisorScope, commentVm.isBusy)
              })
            }
            revalidate()
            repaint()
            awaitCancellation()
          }
        }
      }
    }

  private fun CoroutineScope.createContent(vm: GHPRTimelineThreadViewModel): JComponent {
    val cs = this
    val diff = createDiff(vm)
    val diffAndText = VerticalListPanel(Thread.DIFF_TEXT_GAP).apply {
      var showJob: Job? = null
      combine(vm.collapsed, vm.mainCommentVm) { collapsed, commentVm ->
        showJob?.cancelAndJoin()
        showJob = cs.launchNow {
          if (commentVm == null) {
            add(diff)
          }
          else if (collapsed) {
            val textPane = createCollapsedThreadCommentBody(commentVm)
            add(textPane)
            add(diff)
          }
          else {
            val editableText = GHPRReviewThreadCommentComponentFactory.createCommentBodyIn(this, commentVm)
            add(diff)
            add(editableText)
          }
          revalidate()
          repaint()
          try {
            awaitCancellation()
          }
          finally {
            removeAll()
          }
        }
      }.launchNowIn(cs)
    }

    val avatarProviderWrapper = IconsProvider<CodeReviewUser> { key, iconSize -> vm.avatarIconsProvider.getIcon(key?.avatarUrl, iconSize) }
    val collapsedThreadActionsComponent = CodeReviewCommentUIUtil.createFoldedThreadControlsIn(cs, vm, avatarProviderWrapper).apply {
      bindVisibilityIn(cs, vm.repliesFolded)
    }

    return VerticalListPanel().apply {
      add(diffAndText)
      add(collapsedThreadActionsComponent)
    }
  }

  private fun CoroutineScope.createCollapsedThreadCommentBody(vm: GHPRReviewThreadCommentViewModel): JComponent {
    val cs = this
    val textPane = SimpleHtmlPane().apply {
      foreground = UIUtil.getContextHelpForeground()
      bindTextIn(cs, vm.bodyVm.body)
    }.let { pane ->
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(pane, DimensionRestrictions.LinesHeight(pane, 2, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH))
    }
    return textPane
  }

  private fun CoroutineScope.createDiff(vm: GHPRTimelineThreadViewModel): JComponent {
    val fileNameClickListener = flowOf(ActionListener {
      vm.showDiff()
    })
    return TimelineDiffComponentFactory.createDiffWithHeader(this, vm, vm.filePath, fileNameClickListener) {
      Wrapper().apply {
        bindContentIn(this@createDiffWithHeader, vm.patchHunkWithAnchor) { (hunk, anchorRange) ->
          if (hunk.lines.isEmpty()) {
            JLabel(CollaborationToolsBundle.message("review.thread.diff.not.loaded"))
          }
          else {
            TimelineDiffComponentFactory
              .createDiffComponentIn(this, vm.project, EditorFactory.getInstance(), hunk, anchorRange)
          }
        }
      }
    }
  }

  private fun CoroutineScope.createRepliesPanel(vm: GHPRTimelineThreadViewModel): JComponent {
    val cs = this@createRepliesPanel
    val componentType = CodeReviewChatItemUIUtil.ComponentType.FULL_SECONDARY
    val repliesListPanel = ComponentListPanelFactory.createVertical(cs, vm.replies) { commentVm ->
      GHPRReviewThreadCommentComponentFactory.createIn(this, commentVm, componentType)
    }

    val replyComponent = GHPRReviewThreadComponentFactory.createThreadReplyComponentIn(cs, vm, componentType)
    return VerticalListPanel().apply {
      add(repliesListPanel)
      add(replyComponent)
      bindVisibilityIn(cs, vm.repliesFolded.inverted())
    }
  }
}
