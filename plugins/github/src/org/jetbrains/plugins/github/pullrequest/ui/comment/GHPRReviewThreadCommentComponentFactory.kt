// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentBodyComponentFactory
import javax.swing.JComponent

internal object GHPRReviewThreadCommentComponentFactory {
  fun createIn(cs: CoroutineScope,
               vm: GHPRReviewThreadCommentViewModel,
               componentType: CodeReviewChatItemUIUtil.ComponentType): JComponent {
    val editableText = createCommentBodyIn(cs, vm)
    return CodeReviewChatItemUIUtil.build(componentType,
                                          { vm.avatarIconsProvider.getIcon(vm.author.avatarUrl, it) }, editableText) {
      iconTooltip = vm.author.getPresentableName()
      maxContentWidth = null
      val titlePanel = cs.createTitlePanel(vm)
      val actionsPanel = cs.createCommentActions(vm)
      withHeader(titlePanel, actionsPanel)
    }
  }

  private fun CoroutineScope.createTitlePanel(vm: GHPRReviewThreadCommentViewModel): JComponent {
    val textPanel = CodeReviewTimelineUIUtil.createTitleTextPane(vm.author.getPresentableName(), vm.author.url, vm.createdAt)
    val tagsPanel = createTagsPanel(vm)
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(textPanel)
      add(tagsPanel)
    }
  }

  private fun CoroutineScope.createTagsPanel(vm: GHPRReviewThreadCommentViewModel): JComponent {
    val cs = this
    val resolvedLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag")).apply {
      isVisible = false
      bindVisibilityIn(cs, vm.isFirstInResolvedThread)
    }
    val pendingLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.pending.tag")).apply {
      isVisible = false
      bindVisibilityIn(cs, vm.isPending)
    }
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(resolvedLabel)
      add(pendingLabel)
    }
  }

  fun createCommentBodyIn(cs: CoroutineScope, vm: GHPRReviewThreadCommentViewModel): JComponent {
    val commentPane = GHPRReviewCommentBodyComponentFactory.createIn(cs, vm.bodyVm, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    val editableText = EditableComponentFactory.create(cs, commentPane, vm.editVm) { editVm ->
      val actions = createEditActionsConfig(editVm)
      CodeReviewCommentTextFieldFactory.createIn(this, editVm, actions).let { pane ->
        CollaborationToolsUIUtil
          .wrapWithLimitedSize(pane, DimensionRestrictions.ScalingConstant(width = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH))
      }
    }
    return editableText
  }

  private fun CoroutineScope.createCommentActions(vm: GHPRReviewThreadCommentViewModel): JComponent {
    val cs = this
    return HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      if (vm.canEdit) {
        add(CodeReviewCommentUIUtil.createEditButton {
          vm.editBody()
        }.apply {
          bindDisabledIn(cs, vm.isBusy)
        })
      }
      if (vm.canDelete) {
        add(CodeReviewCommentUIUtil.createDeleteCommentIconButton {
          vm.delete()
        }.apply {
          bindDisabledIn(cs, vm.isBusy)
        })
      }
    }
  }
}