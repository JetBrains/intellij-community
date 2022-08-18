// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadComponent
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.Dimension
import java.util.*
import javax.swing.*
import kotlin.math.ceil
import kotlin.math.floor

class GHPRTimelineItemComponentFactory(private val project: Project,
                                       private val detailsDataProvider: GHPRDetailsDataProvider,
                                       private val commentsDataProvider: GHPRCommentsDataProvider,
                                       private val reviewDataProvider: GHPRReviewDataProvider,
                                       private val avatarIconsProvider: GHAvatarIconsProvider,
                                       private val reviewsThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                       private val reviewDiffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                                       private val eventComponentFactory: GHPRTimelineEventComponentFactory<GHPRTimelineEvent>,
                                       private val selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                                       private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                       private val currentUser: GHUser) : TimelineItemComponentFactory<GHPRTimelineItem> {

  override fun createComponent(item: GHPRTimelineItem): Item {
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
      LOG.warn(e)
      return Item(AllIcons.General.Warning, HtmlEditorPane(GithubBundle.message("cannot.display.item", e.message ?: "")))
    }
  }

  private fun createComponent(commit: GHPullRequestCommitShort): Item {
    val gitCommit = commit.commit
    val titlePanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      add(userAvatar(gitCommit.author))
      add(HtmlEditorPane(gitCommit.messageHeadlineHTML))
      add(ActionLink(gitCommit.abbreviatedOid) {
        selectInToolWindowHelper.selectCommit(gitCommit.abbreviatedOid)
      })
    }

    return Item(AllIcons.Vcs.CommitNode, titlePanel)
  }

  fun createComponent(details: GHPullRequestShort): Item {
    val contentPanel: JPanel?
    val actionsPanel: JPanel?
    if (details is GHPullRequest) {
      val textPane = HtmlEditorPane(details.body.convertToHtml(project))
      val panelHandle = GHEditableHtmlPaneHandle(project, textPane, details::body) { newText ->
        detailsDataProvider.updateDetails(EmptyProgressIndicator(), description = newText)
          .successOnEdt { textPane.setBody(it.body.convertToHtml(project)) }
      }
      contentPanel = panelHandle.panel
      actionsPanel = if (details.viewerCanUpdate) NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
        add(GHTextActions.createEditButton(panelHandle))
      }
      else null
    }
    else {
      contentPanel = null
      actionsPanel = null
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(12))).apply {
      add(actionTitle(details.author, GithubBundle.message("pull.request.timeline.created"), details.createdAt))
      if (actionsPanel != null && actionsPanel.componentCount > 0) add(actionsPanel)
    }

    return Item(userAvatar(details.author), titlePanel, contentPanel)
  }

  private fun createComponent(comment: GHIssueComment): Item {
    val textPane = HtmlEditorPane(comment.body.convertToHtml(project))
    val panelHandle = GHEditableHtmlPaneHandle(project, textPane, comment::body) { newText ->
      commentsDataProvider.updateComment(EmptyProgressIndicator(), comment.id, newText)
        .successOnEdt { textPane.setBody(it.convertToHtml(project)) }
    }
    val actionsPanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      if (comment.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
      if (comment.viewerCanDelete) add(GHTextActions.createDeleteButton {
        commentsDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
      })
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(12))).apply {
      add(actionTitle(comment.author, GithubBundle.message("pull.request.timeline.commented"), comment.createdAt))
      if (actionsPanel.componentCount > 0) add(actionsPanel)
    }

    return Item(userAvatar(comment.author), titlePanel, panelHandle.panel)
  }

  private fun createComponent(review: GHPullRequestReview): Item {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)
    val panelHandle: GHEditableHtmlPaneHandle?
    if (review.body.isNotEmpty()) {
      val textPane = HtmlEditorPane(review.body.convertToHtml(project))
      panelHandle =
        GHEditableHtmlPaneHandle(project, textPane, review::body, { newText ->
          reviewDataProvider.updateReviewBody(EmptyProgressIndicator(), review.id, newText)
            .successOnEdt { textPane.setBody(it.convertToHtml(project)) }
        })
    }
    else {
      panelHandle = null
    }

    val actionsPanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      if (panelHandle != null && review.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
    }

    val contentPanel = NonOpaquePanel(VerticalLayout(12)).apply {
      border = JBUI.Borders.emptyTop(4)
      if (panelHandle != null) add(panelHandle.panel)
      add(GHPRReviewThreadsPanel.create(reviewThreadsModel) {
        GHPRReviewThreadComponent.createWithDiff(project, it,
                                                 reviewDataProvider, avatarIconsProvider,
                                                 reviewDiffComponentFactory,
                                                 selectInToolWindowHelper, suggestedChangeHelper,
                                                 currentUser)
      })
    }
    val actionText = when (review.state) {
      APPROVED -> GithubBundle.message("pull.request.timeline.approved.changes")
      CHANGES_REQUESTED -> GithubBundle.message("pull.request.timeline.requested.changes")
      PENDING -> GithubBundle.message("pull.request.timeline.started.review")
      COMMENTED, DISMISSED -> GithubBundle.message("pull.request.timeline.reviewed")
    }
    val titlePanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(12))).apply {
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

    return Item(icon, titlePanel, contentPanel, NOT_DEFINED_SIZE)
  }

  private fun userAvatar(user: GHActor?): JLabel {
    return userAvatar(avatarIconsProvider, user)
  }

  private fun userAvatar(user: GHGitActor?): JLabel {
    return LinkLabel<Any>("", avatarIconsProvider.getIcon(user?.avatarUrl), LinkListener { _, _ ->
      user?.url?.let { BrowserUtil.browse(it) }
    })
  }

  class Item(val marker: JLabel, title: JComponent, content: JComponent? = null, size: Dimension = getDefaultSize()) : JPanel() {

    constructor(markerIcon: Icon, title: JComponent, content: JComponent? = null, size: Dimension = getDefaultSize())
      : this(createMarkerLabel(markerIcon), title, content, size)

    init {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill()).apply {
        columnConstraints = "[]${JBUIScale.scale(8)}[]"
      }

      add(marker, CC().pushY())
      add(title, CC().pushX())
      if (content != null) add(content, CC().newline().skip().grow().push().maxWidth(size))
    }

    companion object {
      private fun CC.maxWidth(dimension: Dimension) = if (dimension.width > 0) this.maxWidth("${dimension.width}") else this

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
    private val LOG = logger<GHPRTimelineItemComponentFactory>()
    private val NOT_DEFINED_SIZE = Dimension(-1, -1)

    fun getDefaultSize() = Dimension(GHUIUtil.getPRTimelineWidth(), -1)

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
      val text = """<a href='${actor?.url}'>${actor?.login ?: "unknown"}</a> $actionHTML ${GHUIUtil.formatActionDate(date)}"""

      return HtmlEditorPane(text).apply {
        foreground = UIUtil.getContextHelpForeground()
      }
    }
  }
}