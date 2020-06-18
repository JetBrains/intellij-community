// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadComponent
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.*
import javax.swing.*
import kotlin.math.ceil
import kotlin.math.floor

class GHPRTimelineItemComponentFactory(private val detailsDataProvider: GHPRDetailsDataProvider,
                                       private val commentsDataProvider: GHPRCommentsDataProvider,
                                       private val reviewDataProvider: GHPRReviewDataProvider,
                                       private val avatarIconsProvider: GHAvatarIconsProvider,
                                       private val reviewsThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                       private val reviewDiffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                                       private val eventComponentFactory: GHPRTimelineEventComponentFactory<GHPRTimelineEvent>,
                                       private val selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                                       private val currentUser: GHUser) {

  fun createComponent(item: GHPRTimelineItem): Item {
    try {
      return when (item) {
        is GHPullRequestCommitShort -> createComponent(item)

        is GHIssueComment -> createComponent(item)
        is GHPullRequestReview -> createComponent(item)

        is GHPRTimelineEvent -> eventComponentFactory.createComponent(item)
        is GHPRTimelineItem.Unknown -> throw IllegalStateException("Unknown item type: " + item.__typename)
        else -> error("Undefined item type")
      }
    }
    catch (e: Exception) {
      return Item(AllIcons.General.Warning, HtmlEditorPane(GithubBundle.message("cannot.display.item", e.message ?: "")))
    }
  }

  private fun createComponent(commit: GHPullRequestCommitShort): Item {
    val gitCommit = commit.commit
    val titlePanel = NonOpaquePanel(HorizontalLayout(UI.scale(8))).apply {
      add(userAvatar(gitCommit.author))
      add(HtmlEditorPane(gitCommit.messageHeadlineHTML))
      add(LinkLabel<Any?>(gitCommit.abbreviatedOid, null) { _, _ ->
        selectInToolWindowHelper.selectCommit(gitCommit.abbreviatedOid)
      })
    }

    return Item(AllIcons.Vcs.CommitNode, titlePanel)
  }

  fun createComponent(details: GHPullRequestShort): Item {
    val contentPanel: JPanel?
    val actionsPanel: JPanel?
    if (details is GHPullRequest) {
      val textPane = HtmlEditorPane(details.bodyHTML)
      val panelHandle = GHEditableHtmlPaneHandle(textPane,
                                                 { detailsDataProvider.getDescriptionMarkdownBody(EmptyProgressIndicator()) },
                                                 { newText ->
                                                   detailsDataProvider.updateDetails(EmptyProgressIndicator(), newText).successOnEdt {
                                                     textPane.setBody(it.bodyHTML)
                                                   }
                                                 })
      contentPanel = panelHandle.panel
      actionsPanel = if (details.viewerCanUpdate) NonOpaquePanel(HorizontalLayout(UI.scale(8))).apply {
        add(GHTextActions.createEditButton(panelHandle))
      }
      else null
    }
    else {
      contentPanel = null
      actionsPanel = null
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(UI.scale(12))).apply {
      add(actionTitle(details.author, GithubBundle.message("pull.request.timeline.created"), details.createdAt))
      if (actionsPanel != null && actionsPanel.componentCount > 0) add(actionsPanel)
    }

    return Item(userAvatar(details.author), titlePanel, contentPanel)
  }

  private fun createComponent(comment: GHIssueComment): Item {
    val textPane = HtmlEditorPane(comment.bodyHTML)
    val panelHandle = GHEditableHtmlPaneHandle(textPane,
                                               { commentsDataProvider.getCommentMarkdownBody(EmptyProgressIndicator(), comment.id) },
                                               { newText ->
                                                 commentsDataProvider.updateComment(EmptyProgressIndicator(), comment.id,
                                                                                    newText).successOnEdt { textPane.setBody(it) }
                                               })
    val actionsPanel = NonOpaquePanel(HorizontalLayout(UI.scale(8))).apply {
      if (comment.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
      if (comment.viewerCanDelete) add(GHTextActions.createDeleteButton {
        commentsDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
      })
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(UI.scale(12))).apply {
      add(actionTitle(comment.author, GithubBundle.message("pull.request.timeline.commented"), comment.createdAt))
      if (actionsPanel.componentCount > 0) add(actionsPanel)
    }

    return Item(userAvatar(comment.author), titlePanel, panelHandle.panel)
  }

  private fun createComponent(review: GHPullRequestReview): Item {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)
    val panelHandle: GHEditableHtmlPaneHandle?
    if (review.bodyHTML.isNotEmpty()) {
      val editorPane = HtmlEditorPane(review.bodyHTML)
      panelHandle =
        GHEditableHtmlPaneHandle(editorPane,
                                 { reviewDataProvider.getReviewMarkdownBody(EmptyProgressIndicator(), review.id) },
                                 { newText ->
                                   reviewDataProvider.updateReviewBody(EmptyProgressIndicator(), review.id, newText).successOnEdt {
                                     editorPane.setBody(it)
                                   }
                                 })
    }
    else {
      panelHandle = null
    }

    val actionsPanel = NonOpaquePanel(HorizontalLayout(UI.scale(8))).apply {
      if (panelHandle != null && review.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
    }

    val contentPanel = NonOpaquePanel(VerticalLayout(UI.scale(12))).apply {
      border = JBUI.Borders.emptyTop(4)
      if (panelHandle != null) add(panelHandle.panel, VerticalLayout.FILL_HORIZONTAL)
      add(GHPRReviewThreadsPanel.create(reviewThreadsModel) {
        GHPRReviewThreadComponent.createWithDiff(it, reviewDataProvider, reviewDiffComponentFactory, avatarIconsProvider, currentUser)
      }, VerticalLayout.FILL_HORIZONTAL)
    }
    val actionText = when (review.state) {
      APPROVED -> GithubBundle.message("pull.request.timeline.approved.changes")
      CHANGES_REQUESTED -> GithubBundle.message("pull.request.timeline.rejected.changes")
      PENDING -> GithubBundle.message("pull.request.timeline.started.review")
      COMMENTED, DISMISSED -> GithubBundle.message("pull.request.timeline.reviewed")
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(UI.scale(12))).apply {
      add(actionTitle(avatarIconsProvider, review.author, actionText, review.createdAt))
      if (actionsPanel.componentCount > 0) add(actionsPanel)
    }

    val icon = when (review.state) {
      APPROVED -> GithubIcons.ReviewAccepted
      CHANGES_REQUESTED -> GithubIcons.ReviewRejected
      COMMENTED -> GithubIcons.Review
      DISMISSED -> GithubIcons.Review
      PENDING -> GithubIcons.Review
    }

    return Item(icon, titlePanel, contentPanel)
  }

  private fun userAvatar(user: GHActor?): JLabel {
    return userAvatar(avatarIconsProvider, user)
  }

  private fun userAvatar(user: GHGitActor?): JLabel {
    return LinkLabel<Any>("", avatarIconsProvider.getIcon(user?.avatarUrl), LinkListener { _, _ ->
      user?.url?.let { BrowserUtil.browse(it) }
    })
  }

  class Item(val marker: JLabel, title: JComponent, content: JComponent? = null) : JPanel() {

    constructor(markerIcon: Icon, title: JComponent, content: JComponent? = null)
      : this(createMarkerLabel(markerIcon), title, content)

    init {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill()).apply {
        columnConstraints = "[]${UI.scale(8)}[]"
      }

      add(marker, CC().pushY())
      add(title, CC().pushX())
      if (content != null) add(content, CC().newline().skip().grow().push())
    }

    companion object {
      private fun createMarkerLabel(markerIcon: Icon) =
        JLabel(markerIcon).apply {
          val verticalGap = if (markerIcon.iconHeight < 20) (20f - markerIcon.iconHeight) / 2 else 0f
          val horizontalGap = if (markerIcon.iconWidth < 20) (20f - markerIcon.iconWidth) / 2 else 0f
          border = JBUI.Borders.empty(floor(verticalGap).toInt(), floor(horizontalGap).toInt(),
                                      ceil(verticalGap).toInt(), ceil(horizontalGap).toInt())
        }
    }
  }

  companion object {
    fun userAvatar(avatarIconsProvider: GHAvatarIconsProvider, user: GHActor?): JLabel {
      return LinkLabel<Any>("", avatarIconsProvider.getIcon(user?.avatarUrl), LinkListener { _, _ ->
        user?.url?.let { BrowserUtil.browse(it) }
      })
    }

    fun actionTitle(avatarIconsProvider: GHAvatarIconsProvider, actor: GHActor?, @Language("HTML") actionHTML: String, date: Date)
      : JComponent {
      return HorizontalBox().apply {
        add(userAvatar(avatarIconsProvider, actor))
        add(Box.createRigidArea(JBDimension(8, 0)))
        add(actionTitle(actor, actionHTML, date))
      }
    }

    fun actionTitle(actor: GHActor?, actionHTML: String, date: Date): JComponent {
      //language=HTML
      val text = """<a href='${actor?.url}'>${actor?.login ?: "unknown"}</a> $actionHTML ${GithubUIUtil.formatActionDate(date)}"""

      return HtmlEditorPane(text).apply {
        foreground = UIUtil.getContextHelpForeground()
      }
    }
  }
}
