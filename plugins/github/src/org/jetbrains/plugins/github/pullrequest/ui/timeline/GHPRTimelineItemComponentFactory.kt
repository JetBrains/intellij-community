// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.onHyperlinkActivated
import com.intellij.collaboration.ui.codereview.setHtmlBody
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.JBDateFormat
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHActor
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
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel

class GHPRTimelineItemComponentFactory(private val project: Project,
                                       private val detailsDataProvider: GHPRDetailsDataProvider,
                                       private val commentsDataProvider: GHPRCommentsDataProvider,
                                       private val reviewDataProvider: GHPRReviewDataProvider,
                                       private val avatarIconsProvider: GHAvatarIconsProvider,
                                       private val reviewsThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                       private val reviewDiffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                                       private val selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                                       private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                       private val ghostUser: GHUser,
                                       private val prAuthor: GHActor?,
                                       private val currentUser: GHUser) : TimelineItemComponentFactory<GHPRTimelineItem> {

  private val eventComponentFactory = GHPRTimelineEventComponentFactoryImpl(avatarIconsProvider, ghostUser)

  override fun createComponent(item: GHPRTimelineItem): JComponent {
    try {
      return when (item) {
        is GHPullRequestCommitShort -> createComponent(listOf(item))
        is GHPRTimelineGroupedCommits -> createComponent(item.items)

        is GHIssueComment -> createComponent(item)
        is GHPullRequestReview -> createComponent(item)

        is GHPRTimelineEvent -> eventComponentFactory.createComponent(item)
        is GHPRTimelineItem.Unknown -> throw IllegalStateException("Unknown item type: " + item.__typename)
        else -> error("Undefined item type")
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
      return createItem(prAuthor, null, HtmlEditorPane(GithubBundle.message("cannot.display.item", e.message ?: "")))
    }
  }

  private fun createComponent(commits: List<GHPullRequestCommitShort>): JComponent {
    val commitsPanels = commits.asSequence()
      .map { it.commit }
      .map {
        val builder = HtmlBuilder()
          .append(HtmlChunk.p()
                    .children(
                      HtmlChunk.link("$COMMIT_HREF_PREFIX${it.abbreviatedOid}", it.abbreviatedOid),
                      HtmlChunk.nbsp(),
                      HtmlChunk.raw(it.messageHeadlineHTML)
                    ))

        val author = it.author
        if (author != null) {
          val actor = author.user ?: ghostUser
          val date = author.date
          val chunk = HtmlChunk.p().buildChildren {
            append(HtmlChunk.link(actor.url, actor.getPresentableName()))
            if (date != null) {
              append(HtmlChunk.nbsp())
              append(JBDateFormat.getFormatter().formatPrettyDateTime(date))
            }
          }
          builder.append(chunk)
        }
        builder.toString()
      }.map { text ->
        HtmlEditorPane(text).apply {
          removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
          onHyperlinkActivated {
            val href = it.description
            if (href.startsWith(COMMIT_HREF_PREFIX)) {
              selectInToolWindowHelper.selectCommit(href.removePrefix(COMMIT_HREF_PREFIX))
            }
            else {
              BrowserUtil.browse(href)
            }
          }
        }
      }.fold(JPanel(VerticalLayout(4)).apply { isOpaque = false }) { panel, commitPane ->
        panel.apply {
          add(commitPane)
        }
      }

    val commitsCount = commits.size

    val contentPanel = JPanel(VerticalLayout(4)).apply {
      isOpaque = false

      val titleText = if (commitsCount == 1) {
        GithubBundle.message("pull.request.timeline.commit.added")
      }
      else {
        GithubBundle.message("pull.request.timeline.commits.added", commitsCount)
      }

      add(HtmlEditorPane(titleText))
      add(StatusMessageComponentFactory.create(commitsPanels))
    }
    val actor = commits.singleOrNull()?.commit?.author?.user ?: prAuthor ?: ghostUser
    return createItem(actor, commits.singleOrNull()?.commit?.author?.date, contentPanel)
  }

  fun createComponent(details: GHPullRequestShort): JComponent {
    val contentPanel: JPanel?
    val actionsPanel: JPanel?
    if (details is GHPullRequest) {
      val textPane = HtmlEditorPane(details.body.convertToHtml(project))
      val panelHandle = GHEditableHtmlPaneHandle(project, textPane, details::body) { newText ->
        detailsDataProvider.updateDetails(EmptyProgressIndicator(), description = newText)
          .successOnEdt { textPane.setHtmlBody(it.body.convertToHtml(project)) }
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

    return createItem(details.author, details.createdAt, contentPanel ?: JPanel(null), actionsPanel)
  }

  private fun createComponent(comment: GHIssueComment): JComponent {
    val textPane = HtmlEditorPane(comment.body.convertToHtml(project))
    val panelHandle = GHEditableHtmlPaneHandle(project, textPane, comment::body) { newText ->
      commentsDataProvider.updateComment(EmptyProgressIndicator(), comment.id, newText)
        .successOnEdt { textPane.setHtmlBody(it.convertToHtml(project)) }
    }
    val actionsPanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      if (comment.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
      if (comment.viewerCanDelete) add(GHTextActions.createDeleteButton {
        commentsDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
      })
    }

    return createItem(comment.author, comment.createdAt, panelHandle.panel, actionsPanel)
  }

  private fun createComponent(review: GHPullRequestReview): JComponent {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)
    val panelHandle: GHEditableHtmlPaneHandle?
    if (review.body.isNotEmpty()) {
      val textPane = HtmlEditorPane(review.body.convertToHtml(project))
      panelHandle =
        GHEditableHtmlPaneHandle(project, textPane, review::body, { newText ->
          reviewDataProvider.updateReviewBody(EmptyProgressIndicator(), review.id, newText)
            .successOnEdt { textPane.setHtmlBody(it.convertToHtml(project)) }
        })
    }
    else {
      panelHandle = null
    }

    val actionsPanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(8))).apply {
      if (panelHandle != null && review.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
    }

    val stateText = when (review.state) {
      APPROVED -> GithubBundle.message("pull.request.timeline.approved.changes")
      CHANGES_REQUESTED -> GithubBundle.message("pull.request.timeline.requested.changes")
      PENDING -> GithubBundle.message("pull.request.timeline.started.review")
      COMMENTED, DISMISSED -> GithubBundle.message("pull.request.timeline.reviewed")
    }
    val stateType = when (review.state) {
      APPROVED -> StatusMessageType.SUCCESS
      CHANGES_REQUESTED -> StatusMessageType.ERROR
      PENDING -> StatusMessageType.SECONDARY_INFO
      COMMENTED, DISMISSED -> StatusMessageType.INFO
    }

    val contentPanel = JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC()
                           .fillX()
                           .flowY()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))
      if (panelHandle != null) {
        val commentPanel = panelHandle.panel
        add(commentPanel, CC().grow().push()
          .minWidth("0").maxWidth("${GHPRTimelineItemUIUtil.maxTimelineItemTextWidth}"))
      }

      add(StatusMessageComponentFactory.create(HtmlEditorPane(stateText), stateType), CC().grow().push()
        .minWidth("0").maxWidth("${GHPRTimelineItemUIUtil.maxTimelineItemTextWidth}"))

      val threadsPanel = GHPRReviewThreadsPanel.create(reviewThreadsModel) {
        GHPRReviewThreadComponent.createWithDiff(project, it,
                                                 reviewDataProvider, avatarIconsProvider,
                                                 reviewDiffComponentFactory,
                                                 selectInToolWindowHelper, suggestedChangeHelper,
                                                 currentUser)
      }
      add(threadsPanel, CC().grow().push()
        .minWidth("0")
        .gapTop("${JBUIScale.scale(8)}"))
    }
    return GHPRTimelineItemUIUtil.createItem(avatarIconsProvider, review.author ?: ghostUser, review.createdAt,
                                             contentPanel, Int.MAX_VALUE, actionsPanel)
  }

  private fun createItem(actor: GHActor?, date: Date?, content: JComponent, actionsPanel: JComponent? = null): JComponent =
    GHPRTimelineItemUIUtil.createItem(avatarIconsProvider, actor ?: ghostUser, date, content, actionsPanel)

  companion object {
    private val LOG = logger<GHPRTimelineItemComponentFactory>()

    private const val COMMIT_HREF_PREFIX = "commit://"
  }
}