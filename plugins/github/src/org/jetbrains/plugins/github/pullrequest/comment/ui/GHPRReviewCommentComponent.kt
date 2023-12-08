// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

object GHPRReviewCommentComponent {

  fun create(project: Project,
             thread: GHPRReviewThreadModel,
             comment: GHPRReviewCommentModel,
             ghostUser: GHUser,
             reviewDataProvider: GHPRReviewDataProvider,
             htmlImageLoader: AsyncHtmlImageLoader,
             avatarIconsProvider: GHAvatarIconsProvider,
             suggestedChangeHelper: GHPRSuggestedChangeHelper,
             type: CodeReviewChatItemUIUtil.ComponentType,
             showResolvedMarker: Boolean = true,
             maxContentWidth: Int = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH): JComponent {

    val author = comment.author ?: ghostUser
    val titlePane = CodeReviewTimelineUIUtil.createTitleTextPane(author.getPresentableName(), author.url, comment.dateCreated).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val pendingLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.pending.tag"))
    val resolvedLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag"))

    val commentWrapper = Wrapper().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      isOpaque = false
    }

    val maxTextWidth = maxContentWidth - type.contentLeftShift

    Controller(project,
               thread, comment,
               suggestedChangeHelper, htmlImageLoader,
               pendingLabel, resolvedLabel, commentWrapper,
               showResolvedMarker,
               maxTextWidth)

    val editablePaneHandle = GHEditableHtmlPaneHandle(project, commentWrapper, maxTextWidth, comment::body) {
      reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, it)
    }

    val editButton = CodeReviewCommentUIUtil.createEditButton {
      editablePaneHandle.showAndFocusEditor()
    }.apply {
      isVisible = comment.canBeUpdated
    }
    val deleteButton = CodeReviewCommentUIUtil.createDeleteCommentIconButton {
      reviewDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
    }.apply {
      isVisible = comment.canBeDeleted
    }

    val actionsPanel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      isVisible = editButton.isVisible && deleteButton.isVisible

      add(editButton)
      add(deleteButton)
    }

    val title = HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(titlePane)
      add(pendingLabel)
      add(resolvedLabel)
    }

    return CodeReviewChatItemUIUtil.build(type,
                                          { avatarIconsProvider.getIcon(author.avatarUrl, it) },
                                          editablePaneHandle.panel) {
      iconTooltip = author.getPresentableName()
      withHeader(title, actionsPanel)
      this.maxContentWidth = null
    }
  }

  private class Controller(private val project: Project,
                           private val thread: GHPRReviewThreadModel,
                           private val comment: GHPRReviewCommentModel,
                           private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                           private val htmlmageLoader: AsyncHtmlImageLoader,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val commentWrapper: Wrapper,
                           private val showResolvedMarker: Boolean,
                           private val maxTextWidth: Int) {
    init {
      comment.addAndInvokeChangesListener {
        update()
      }
    }

    private fun update() {
      val commentComponent = createCommentBodyComponent(project, suggestedChangeHelper, thread, htmlmageLoader, comment.body, maxTextWidth)
      commentWrapper.setContent(commentComponent)
      commentWrapper.repaint()

      when (comment.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
        }

        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
        }
      }

      resolvedLabel.isVisible = comment.isFirstInResolvedThread && showResolvedMarker
    }
  }

  fun createCommentBodyComponent(project: Project,
                                 suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                 thread: GHPRReviewThreadModel,
                                 htmlmageLoader: AsyncHtmlImageLoader,
                                 commentBody: @Nls String,
                                 maxTextWidth: Int): JComponent {
    val commentComponentFactory = GHPRReviewCommentComponentFactory(project, htmlmageLoader)
    val hunk = thread.patchHunk ?: PatchHunk(0, 0, 0, 0)
    val lineIndex = thread.location?.second

    val commentComponent = if (lineIndex != null && GHSuggestedChange.containsSuggestedChange(commentBody)) {
      val startLineIndex = thread.startLocation?.second ?: lineIndex
      val suggestedChange = GHSuggestedChange(commentBody, hunk, thread.filePath, startLineIndex, lineIndex)
      commentComponentFactory.createCommentWithSuggestedChangeComponent(thread, suggestedChange, suggestedChangeHelper, maxTextWidth)
    }
    else {
      commentComponentFactory.createCommentComponent(commentBody, maxTextWidth)
    }
    return commentComponent
  }

  fun factory(project: Project,
              thread: GHPRReviewThreadModel,
              ghostUser: GHUser,
              reviewDataProvider: GHPRReviewDataProvider,
              htmlmageLoader: AsyncHtmlImageLoader,
              avatarIconsProvider: GHAvatarIconsProvider,
              suggestedChangeHelper: GHPRSuggestedChangeHelper,
              type: CodeReviewChatItemUIUtil.ComponentType,
              showResolvedMarkerOnFirstComment: Boolean = true,
              maxContentWidth: Int = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(
        project,
        thread, comment, ghostUser,
        reviewDataProvider,
        htmlmageLoader, avatarIconsProvider,
        suggestedChangeHelper,
        type,
        showResolvedMarkerOnFirstComment,
        maxContentWidth)
    }
  }
}
