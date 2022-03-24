// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent
import javax.swing.JPanel


object GHPRReviewCommentComponent {

  fun create(project: Project,
             thread: GHPRReviewThreadModel,
             comment: GHPRReviewCommentModel,
             reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider,
             suggestedChangeHelper: GHPRSuggestedChangeHelper,
             showResolvedMarker: Boolean = true): JComponent {

    val avatarLabel = ActionLink("") {
      comment.authorLinkUrl?.let { BrowserUtil.browse(it) }
    }.apply {
      icon = avatarIconsProvider.getIcon(comment.authorAvatarUrl)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val titlePane = HtmlEditorPane().apply {
      foreground = UIUtil.getContextHelpForeground()
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
               titlePane, pendingLabel, resolvedLabel, commentPanel,
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

    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill(),
                         AC().gap("${JBUIScale.scale(8)}"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().minWidth("0").split(5).alignX("left").pushX())
      add(pendingLabel, CC().hideMode(3).alignX("left"))
      add(resolvedLabel, CC().hideMode(3).alignX("left"))
      add(editButton, CC().hideMode(3).gapBefore("${JBUIScale.scale(12)}"))
      add(deleteButton, CC().hideMode(3).gapBefore("${JBUIScale.scale(8)}"))
      add(editablePaneHandle.panel, CC().newline().skip().push().minWidth("0").minHeight("0").growX().maxWidth("${getMaxWidth()}"))
    }
  }

  private fun getMaxWidth() = GHUIUtil.getPRTimelineWidth() - JBUIScale.scale(GHUIUtil.AVATAR_SIZE) + AllIcons.Actions.Close.iconWidth

  private class Controller(private val project: Project,
                           private val thread: GHPRReviewThreadModel,
                           private val comment: GHPRReviewCommentModel,
                           private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                           private val titlePane: HtmlEditorPane,
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

      val authorLink = HtmlBuilder()
        .appendLink(comment.authorLinkUrl.orEmpty(), comment.authorUsername ?: GithubBundle.message("user.someone"))
        .toString()

      when (comment.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
          titlePane.setBody(authorLink)
        }

        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
          titlePane.setBody(GithubBundle.message("pull.request.review.commented", authorLink,
                                                 GHUIUtil.formatActionDate(comment.dateCreated)))
        }
      }

      resolvedLabel.isVisible = comment.isFirstInResolvedThread && showResolvedMarker
    }
  }

  fun factory(project: Project,
              thread: GHPRReviewThreadModel,
              reviewDataProvider: GHPRReviewDataProvider,
              avatarIconsProvider: GHAvatarIconsProvider,
              suggestedChangeHelper: GHPRSuggestedChangeHelper,
              showResolvedMarkerOnFirstComment: Boolean = true)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(
        project,
        thread, comment,
        reviewDataProvider, avatarIconsProvider,
        suggestedChangeHelper,
        showResolvedMarkerOnFirstComment)
    }
  }
}
