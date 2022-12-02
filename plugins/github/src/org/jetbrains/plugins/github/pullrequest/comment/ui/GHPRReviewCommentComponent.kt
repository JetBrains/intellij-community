// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


object GHPRReviewCommentComponent {

  const val AVATAR_SIZE = GHUIUtil.AVATAR_SIZE
  const val AVATAR_GAP = 10

  fun create(project: Project,
             thread: GHPRReviewThreadModel,
             comment: GHPRReviewCommentModel,
             ghostUser: GHUser,
             reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider,
             suggestedChangeHelper: GHPRSuggestedChangeHelper,
             showResolvedMarker: Boolean = true,
             maxContentWidth: Int = GHUIUtil.TEXT_CONTENT_WIDTH): JComponent {

    val author = comment.author ?: ghostUser
    val avatarLabel = JLabel(avatarIconsProvider.getIcon(author.avatarUrl, AVATAR_SIZE))
    val titlePane = GHPRTimelineItemUIUtil.createTitleTextPane(author, comment.dateCreated).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val pendingLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.pending")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = JBUI.CurrentTheme.Validator.warningBackgroundColor()
    }.andOpaque()
    val resolvedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.resolved")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()

    val commentPanel = JPanel(VerticalLayout(8, VerticalLayout.FILL)).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      isOpaque = false
    }

    Controller(project,
               thread, comment,
               suggestedChangeHelper,
               pendingLabel, resolvedLabel, commentPanel,
               showResolvedMarker)

    val editablePaneHandle = GHEditableHtmlPaneHandle(project, commentPanel, comment::body) {
      reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, it)
    }

    val editButton = GHTextActions.createEditButton(editablePaneHandle).apply {
      isVisible = comment.canBeUpdated
    }
    val deleteButton = GHTextActions.createDeleteButton {
      reviewDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
    }.apply {
      isVisible = comment.canBeDeleted
    }

    val actionsPanel = JPanel(HorizontalLayout(8)).apply {
      isOpaque = false
      isVisible = editButton.isVisible && deleteButton.isVisible

      add(editButton)
      add(deleteButton)
    }

    val maxTextWidth = maxContentWidth.let { it - AVATAR_SIZE - AVATAR_GAP }
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill().hideMode(3),
                         AC().gap("$AVATAR_GAP"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().minWidth("0").split(4).alignX("left").pushX())
      add(pendingLabel, CC().alignX("left"))
      add(resolvedLabel, CC().alignX("left"))
      add(actionsPanel, CC().gapBefore("12:push"))
      add(editablePaneHandle.panel, CC().newline().skip()
        .push()
        .minWidth("0").maxWidth("$maxTextWidth"))
    }
  }

  private class Controller(private val project: Project,
                           private val thread: GHPRReviewThreadModel,
                           private val comment: GHPRReviewCommentModel,
                           private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val commentPanel: JComponent,
                           private val showResolvedMarker: Boolean) {
    init {
      comment.addChangesListener {
        update()
      }
      update()
    }

    private fun update() {
      val commentComponentFactory = GHPRReviewCommentComponentFactory(project)
      val commentComponent = if (GHSuggestedChange.containsSuggestedChange(comment.body)) {
        val suggestedChange = GHSuggestedChange.create(comment.body,
                                                       thread.diffHunk, thread.filePath,
                                                       thread.startLine ?: thread.line, thread.line)
        commentComponentFactory.createCommentWithSuggestedChangeComponent(thread, suggestedChange, suggestedChangeHelper)
      }
      else {
        commentComponentFactory.createCommentComponent(comment.body)
      }

      commentPanel.removeAll()
      commentPanel.add(commentComponent)

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

  fun factory(project: Project,
              thread: GHPRReviewThreadModel,
              ghostUser: GHUser,
              reviewDataProvider: GHPRReviewDataProvider,
              avatarIconsProvider: GHAvatarIconsProvider,
              suggestedChangeHelper: GHPRSuggestedChangeHelper,
              showResolvedMarkerOnFirstComment: Boolean = true,
              maxContentWidth: Int = GHUIUtil.TEXT_CONTENT_WIDTH)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(
        project,
        thread, comment, ghostUser,
        reviewDataProvider, avatarIconsProvider,
        suggestedChangeHelper,
        showResolvedMarkerOnFirstComment,
        maxContentWidth)
    }
  }
}
