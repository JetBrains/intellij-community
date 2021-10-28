// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.convertToHtml
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewCommentComponent {

  fun create(project: Project,
             reviewDataProvider: GHPRReviewDataProvider,
             comment: GHPRReviewCommentModel,
             avatarIconsProvider: GHAvatarIconsProvider,
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

    val textPane = HtmlEditorPane().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }


    Controller(comment, titlePane, pendingLabel, resolvedLabel, textPane, showResolvedMarker)

    val editablePaneHandle = GHEditableHtmlPaneHandle(project,
                                                      textPane,
                                                      { reviewDataProvider.getCommentMarkdownBody(EmptyProgressIndicator(), comment.id) },
                                                      { reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, it) })

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

  private class Controller(private val model: GHPRReviewCommentModel,
                           private val titlePane: HtmlEditorPane,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val bodyPane: HtmlEditorPane,
                           private val showResolvedMarker: Boolean) {
    init {
      model.addChangesListener {
        update()
      }
      update()
    }

    private fun update() {
      bodyPane.setBody(model.body.convertToHtml())

      val authorLink = HtmlBuilder()
        .appendLink(model.authorLinkUrl.orEmpty(), model.authorUsername ?: GithubBundle.message("user.someone"))
        .toString()

      when (model.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
          titlePane.setBody(authorLink)
        }
        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
          titlePane.setBody(GithubBundle.message("pull.request.review.commented", authorLink,
                                                 GHUIUtil.formatActionDate(model.dateCreated)))
        }
      }

      resolvedLabel.isVisible = model.isFirstInResolvedThread && showResolvedMarker
    }
  }

  fun factory(project: Project, reviewDataProvider: GHPRReviewDataProvider, avatarIconsProvider: GHAvatarIconsProvider,
              showResolvedMarkerOnFirstComment: Boolean = true)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(project, reviewDataProvider, comment, avatarIconsProvider, showResolvedMarkerOnFirstComment)
    }
  }
}
