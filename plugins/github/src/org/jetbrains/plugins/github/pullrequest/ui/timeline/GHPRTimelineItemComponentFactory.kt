// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.codereview.onHyperlinkActivated
import com.intellij.collaboration.ui.codereview.setHtmlBody
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
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
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentComponent
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadComponent
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.H_SIDE_BORDER
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.TIMELINE_CONTENT_WIDTH
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.TIMELINE_ICON_AND_GAP_WIDTH
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.TIMELINE_ITEM_WIDTH
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

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

    val contentPanel = JPanel(VerticalLayout(4, SwingConstants.LEFT)).apply {
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

  private val noDescriptionHtmlText by lazy {
    HtmlBuilder()
      .append(GithubBundle.message("pull.request.timeline.no.description"))
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))
      .wrapWith("i")
      .toString()
  }

  fun createComponent(details: GHPullRequestShort): JComponent {
    val contentPanel: JPanel?
    val actionsPanel: JPanel?
    if (details is GHPullRequest) {
      val textPane = HtmlEditorPane()
      fun HtmlEditorPane.updateText(body: @Nls String) {
        val text = body.takeIf { it.isNotBlank() }?.convertToHtml(project) ?: noDescriptionHtmlText
        setBody(text)
      }
      textPane.updateText(details.body)

      val panelHandle = GHEditableHtmlPaneHandle(project, textPane, details::body) { newText ->
        detailsDataProvider.updateDetails(EmptyProgressIndicator(), description = newText)
          .successOnEdt { textPane.updateText(it.body) }
      }
      contentPanel = panelHandle.panel
      actionsPanel = if (details.viewerCanUpdate) NonOpaquePanel(HorizontalLayout(8)).apply {
        add(GHTextActions.createEditButton(panelHandle))
      }
      else null
    }
    else {
      contentPanel = NonOpaquePanel(SingleComponentCenteringLayout()).apply {
        add(JLabel(AnimatedIcon.Default()))
      }
      actionsPanel = null
    }

    return createItem(details.author, details.createdAt, contentPanel, actionsPanel)
  }

  private fun createComponent(comment: GHIssueComment): JComponent {
    val textPane = HtmlEditorPane(comment.body.convertToHtml(project))
    val panelHandle = GHEditableHtmlPaneHandle(project, textPane, comment::body) { newText ->
      commentsDataProvider.updateComment(EmptyProgressIndicator(), comment.id, newText)
        .successOnEdt { textPane.setHtmlBody(it.convertToHtml(project)) }
    }
    val actionsPanel = NonOpaquePanel(HorizontalLayout(8)).apply {
      if (comment.viewerCanUpdate) add(GHTextActions.createEditButton(panelHandle))
      if (comment.viewerCanDelete) add(GHTextActions.createDeleteButton {
        commentsDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
      })
    }

    return createItem(comment.author, comment.createdAt, panelHandle.panel, actionsPanel)
  }

  private fun createComponent(review: GHPullRequestReview): JComponent {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)

    val loadingLabel = JLabel(ApplicationBundle.message("label.loading.page.please.wait")).apply {
      foreground = UIUtil.getContextHelpForeground()
      isVisible = !reviewThreadsModel.loaded
    }

    reviewThreadsModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent?) {
        loadingLabel.isVisible = !reviewThreadsModel.loaded
      }

      override fun intervalRemoved(e: ListDataEvent?) {
        loadingLabel.isVisible = !reviewThreadsModel.loaded
      }

      override fun contentsChanged(e: ListDataEvent?) {
        loadingLabel.isVisible = !reviewThreadsModel.loaded
      }
    })

    val threadsPanel = GHPRReviewThreadsPanel.create(reviewThreadsModel) { thread ->
      createThreadItem(thread)
    }

    val reviewItem = createReviewContentItem(review)
    return JPanel(MigLayout(LC().hideMode(3)
                              .gridGap("0", "0")
                              .insets("0")
                              .flowY()
                              .fill())).apply {
      isOpaque = false

      add(loadingLabel, CC().grow().push()
        .alignX("center")
        .maxWidth("$TIMELINE_CONTENT_WIDTH")
        .gapLeft("$H_SIDE_BORDER")
        .gapRight("$H_SIDE_BORDER"))
      add(threadsPanel, CC().grow().push())
      add(reviewItem, CC().grow().push())
    }
  }

  private fun createThreadItem(thread: GHPRReviewThreadModel): JComponent {
    val coroutineScopeProvider = ActivatableCoroutineScopeProvider()

    val firstComment: GHPRReviewCommentModel = (if (thread.size <= 0) null else thread.getElementAt(0))
                                               ?: return JPanel(null)

    val tagsPanel = createThreadTagsPanel(thread)

    val bodyPanel = Wrapper()
    val textFlow = MutableStateFlow<@Nls String>(firstComment.body)
    firstComment.addChangesListener { textFlow.value = firstComment.body }

    val panelHandle = GHEditableHtmlPaneHandle(project, bodyPanel, firstComment::body) { newText ->
      reviewDataProvider.updateComment(EmptyProgressIndicator(), firstComment.id, newText)
        .successOnEdt { firstComment.update(it) }
    }

    val actionsPanel = NonOpaquePanel(HorizontalLayout(8)).apply {
      if (firstComment.canBeUpdated) add(GHTextActions.createEditButton(panelHandle))
      if (firstComment.canBeDeleted) add(GHTextActions.createDeleteButton {
        reviewDataProvider.deleteComment(EmptyProgressIndicator(), firstComment.id)
      })
    }

    val diff = GHPRReviewThreadComponent.createThreadDiff(thread, reviewDiffComponentFactory, selectInToolWindowHelper)

    val repliesCollapsedState = MutableStateFlow(true)
    coroutineScopeProvider.launchInScope {
      thread.collapsedState.collect {
        if (it) repliesCollapsedState.value = true
      }
    }

    coroutineScopeProvider.launchInScope {
      repliesCollapsedState.collect {
        if (!it) thread.collapsedState.value = false
      }
    }

    val collapsedThreadActionsComponent = GHPRReviewThreadComponent
      .getCollapsedThreadActionsComponent(reviewDataProvider, avatarIconsProvider, thread, repliesCollapsedState, ghostUser).apply {
        border = JBUI.Borders.empty(3, 0, 7, 0)
      }

    val content = JPanel(MigLayout(LC()
                                     .fill().flowY()
                                     .hideMode(3)
                                     .gridGap("0", "4")
                                     .insets("0", "0", "0", "0"))).apply {
      isOpaque = false
    }.apply {
      coroutineScopeProvider.launchInScope {
        combineAndCollect(thread.collapsedState, textFlow) { collapsed, text ->
          removeAll()
          if (collapsed) {
            panelHandle.maxPaneHeight = UIUtil.getUnscaledLineHeight(bodyPanel) * 2
            val textPane = HtmlEditorPane(text).apply {
              foreground = UIUtil.getContextHelpForeground()
            }

            bodyPanel.setContent(textPane)
            add(panelHandle.panel, CC()
              .grow()
              .maxWidth("$TIMELINE_CONTENT_WIDTH"))
            add(diff, CC().grow())
            add(collapsedThreadActionsComponent, CC()
              .maxWidth("$TIMELINE_CONTENT_WIDTH"))
          }
          else {
            panelHandle.maxPaneHeight = null
            val commentComponent = GHPRReviewCommentComponent
              .createCommentBodyComponent(project, suggestedChangeHelper, thread, text)
            bodyPanel.setContent(commentComponent)
            add(diff, CC().grow())
            add(panelHandle.panel, CC()
              .grow()
              .maxWidth("$TIMELINE_CONTENT_WIDTH"))
            add(collapsedThreadActionsComponent, CC()
              .maxWidth("$TIMELINE_CONTENT_WIDTH"))
          }
          revalidate()
          repaint()
        }
      }
    }.also {
      coroutineScopeProvider.activateWith(it)
    }

    val mainItem = GHPRTimelineItemUIUtil.createItem(avatarIconsProvider, firstComment.author ?: ghostUser, firstComment.dateCreated,
                                                     content, Int.MAX_VALUE,
                                                     additionalTitle = tagsPanel,
                                                     actionsPanel = actionsPanel)

    val leftGap = H_SIDE_BORDER + TIMELINE_ICON_AND_GAP_WIDTH + 2
    val commentComponentFactory = GHPRReviewCommentComponent.factory(project, thread, ghostUser,
                                                                     reviewDataProvider, avatarIconsProvider,
                                                                     suggestedChangeHelper,
                                                                     false,
                                                                     TIMELINE_CONTENT_WIDTH) {
      it.border = JBUI.Borders.empty(GHPRReviewCommentComponent.GAP_TOP, leftGap, GHPRReviewCommentComponent.GAP_TOP, H_SIDE_BORDER)
      GHPRTimelineItemUIUtil.withHoverHighlight(it)
    }


    val commentsListPanel = ComponentListPanelFactory.createVertical(thread.repliesModel, commentComponentFactory, 0)

    val commentsPanel = if (reviewDataProvider.canComment()) {
      val layout = MigLayout(LC()
                               .flowY()
                               .fill()
                               .gridGap("0", "0")
                               .insets("0"))

      val actionsComponent = GHPRReviewThreadComponent
        .createUncollapsedThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser) {}.apply {
          border = JBUI.Borders.empty(6, leftGap, 6, H_SIDE_BORDER)
        }

      JPanel(layout).apply {
        isOpaque = false
        add(commentsListPanel,
            CC().grow().push()
              .minWidth("0"))
        add(actionsComponent,
            CC().grow().push()
              .minWidth("0").maxWidth("$TIMELINE_ITEM_WIDTH"))
      }
    }
    else {
      commentsListPanel
    }

    coroutineScopeProvider.launchInScope {
      repliesCollapsedState.collect {
        collapsedThreadActionsComponent.isVisible = it
        commentsPanel.isVisible = !it
      }
    }

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(mainItem)
      add(commentsPanel)
    }
  }

  private fun createThreadTagsPanel(thread: GHPRReviewThreadModel): JPanel {
    val outdatedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.thread.outdated")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()

    val resolvedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.resolved")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()

    thread.addAndInvokeStateChangeListener {
      outdatedLabel.isVisible = thread.isOutdated
      resolvedLabel.isVisible = thread.isResolved
    }

    val tagsPanel = JPanel(HorizontalLayout(10)).apply {
      isOpaque = false
      add(outdatedLabel)
      add(resolvedLabel)
    }
    return tagsPanel
  }

  private fun createReviewContentItem(review: GHPullRequestReview): JComponent {
    val panelHandle: GHEditableHtmlPaneHandle?
    if (review.body.isNotEmpty()) {
      val textPane = HtmlEditorPane(review.body.convertToHtml(project))
      panelHandle =
        GHEditableHtmlPaneHandle(project, textPane, review::body) { newText ->
          reviewDataProvider.updateReviewBody(EmptyProgressIndicator(), review.id, newText)
            .successOnEdt { textPane.setHtmlBody(it.convertToHtml(project)) }
        }
    }
    else {
      panelHandle = null
    }

    val actionsPanel = NonOpaquePanel(HorizontalLayout(8)).apply {
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
          .minWidth("0").maxWidth("$TIMELINE_CONTENT_WIDTH"))
      }

      add(StatusMessageComponentFactory.create(HtmlEditorPane(stateText), stateType), CC().grow().push()
        .minWidth("0").maxWidth("$TIMELINE_CONTENT_WIDTH"))
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