// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewCommentComponent {

  fun create(reviewDataProvider: GHPRReviewDataProvider,
             comment: GHPRReviewCommentModel,
             avatarIconsProvider: GHAvatarIconsProvider): JComponent {

    val avatarLabel: LinkLabel<*> = LinkLabel.create("") {
      comment.authorLinkUrl?.let { BrowserUtil.browse(it) }
    }.apply {
      icon = avatarIconsProvider.getIcon(comment.authorAvatarUrl)
      isFocusable = true
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


    Controller(comment, titlePane, pendingLabel, resolvedLabel, textPane)

    val editorWrapper = Wrapper()
    val editButton = GHTextActions.createEditButton(textPane, editorWrapper,
                                                    { reviewDataProvider.getCommentMarkdownBody(EmptyProgressIndicator(), comment.id) },
                                                    { reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, it) }).apply {
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
                         AC().gap("${UI.scale(8)}"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().minWidth("0").split(5).alignX("left").pushX())
      add(pendingLabel, CC().hideMode(3).alignX("left"))
      add(resolvedLabel, CC().hideMode(3).alignX("left"))
      add(editButton, CC().hideMode(3).gapBefore("${UI.scale(12)}"))
      add(deleteButton, CC().hideMode(3).gapBefore("${UI.scale(8)}"))
      add(textPane, CC().newline().skip().push().minWidth("0").minHeight("0"))
      add(editorWrapper, CC().newline().skip().push().minWidth("0").minHeight("0").growX())
    }
  }

  private class Controller(private val model: GHPRReviewCommentModel,
                           private val titlePane: HtmlEditorPane,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val bodyPane: HtmlEditorPane) {
    init {
      model.addChangesListener {
        update()
      }
      update()
    }

    private fun update() {
      bodyPane.setBody(model.body)

      val href = model.authorLinkUrl?.let { """href='${it}'""" }.orEmpty()
      //language=HTML
      val authorName = """<a $href>${model.authorUsername ?: "unknown"}</a>"""

      when (model.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
          titlePane.text = authorName
        }
        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
          titlePane.text = GithubBundle.message("pull.request.review.commented", authorName,
                                                GithubUIUtil.formatActionDate(model.dateCreated))
        }
      }

      resolvedLabel.isVisible = model.isFirstInResolvedThread
    }
  }

  fun factory(reviewDataProvider: GHPRReviewDataProvider, avatarIconsProvider: GHAvatarIconsProvider)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(reviewDataProvider, comment, avatarIconsProvider)
    }
  }
}