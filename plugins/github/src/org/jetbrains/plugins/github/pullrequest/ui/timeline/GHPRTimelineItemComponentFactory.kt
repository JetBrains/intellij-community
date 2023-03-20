// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil.Title
import com.intellij.collaboration.ui.codereview.onHyperlinkActivated
import com.intellij.collaboration.ui.codereview.setHtmlBody
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState.*
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
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.buildTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createTitlePane
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRTimelineItemComponentFactory(private val project: Project,
                                       private val detailsDataProvider: GHPRDetailsDataProvider,
                                       private val commentsDataProvider: GHPRCommentsDataProvider,
                                       private val reviewDataProvider: GHPRReviewDataProvider,
                                       private val avatarIconsProvider: GHAvatarIconsProvider,
                                       private val reviewsThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                       private val selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                                       private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                       private val ghostUser: GHUser,
                                       private val prAuthor: GHActor?,
                                       private val currentUser: GHUser)
  : (GHPRTimelineItem) -> JComponent {

  private val eventComponentFactory = GHPRTimelineEventComponentFactoryImpl(avatarIconsProvider, ghostUser)

  override fun invoke(item: GHPRTimelineItem): JComponent {
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
      return createTimelineItem(avatarIconsProvider, prAuthor ?: ghostUser, null,
                                HtmlEditorPane(GithubBundle.message("cannot.display.item", e.message ?: "")))
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
      }.fold(VerticalListPanel(4)) { panel, commitPane ->
        panel.apply {
          add(commitPane)
        }
      }

    val commitsCount = commits.size

    val contentPanel = VerticalListPanel(4).apply {
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
    return createTimelineItem(avatarIconsProvider, actor ?: ghostUser, commits.singleOrNull()?.commit?.author?.date, contentPanel)
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
      actionsPanel = if (details.viewerCanUpdate) HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
        add(CodeReviewCommentUIUtil.createEditButton {
          panelHandle.showAndFocusEditor()
        })
      }
      else null
    }
    else {
      contentPanel = NonOpaquePanel(SingleComponentCenteringLayout()).apply {
        add(JLabel(AnimatedIcon.Default()))
      }
      actionsPanel = null
    }

    return createTimelineItem(avatarIconsProvider, details.author ?: ghostUser, details.createdAt, contentPanel, actionsPanel)
  }

  private fun createComponent(comment: GHIssueComment): JComponent {
    val textPane = HtmlEditorPane(comment.body.convertToHtml(project))
    val panelHandle = GHEditableHtmlPaneHandle(project, textPane, comment::body) { newText ->
      commentsDataProvider.updateComment(EmptyProgressIndicator(), comment.id, newText)
        .successOnEdt { textPane.setHtmlBody(it.convertToHtml(project)) }
    }
    val actionsPanel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      if (comment.viewerCanUpdate) add(CodeReviewCommentUIUtil.createEditButton {
        panelHandle.showAndFocusEditor()
      })
      if (comment.viewerCanDelete) add(CodeReviewCommentUIUtil.createDeleteCommentIconButton {
        commentsDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
      })
    }

    return createTimelineItem(avatarIconsProvider, comment.author ?: ghostUser, comment.createdAt, panelHandle.panel, actionsPanel)
  }

  private fun createComponent(review: GHPullRequestReview): JComponent {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)

    val loadingPanel = JPanel(SingleComponentCenteringLayout()).apply {
      isOpaque = false
      add(JLabel(ApplicationBundle.message("label.loading.page.please.wait")).apply {
        foreground = UIUtil.getContextHelpForeground()
      })
    }.let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = CodeReviewTimelineUIUtil.ITEM_BORDER
      isVisible = !reviewThreadsModel.loaded
    }

    reviewThreadsModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent?) {
        loadingPanel.isVisible = !reviewThreadsModel.loaded
      }

      override fun intervalRemoved(e: ListDataEvent?) {
        loadingPanel.isVisible = !reviewThreadsModel.loaded
      }

      override fun contentsChanged(e: ListDataEvent?) {
        loadingPanel.isVisible = !reviewThreadsModel.loaded
      }
    })

    val threadsPanel = GHPRReviewThreadsPanel.create(reviewThreadsModel) { thread ->
      createThreadItem(thread)
    }

    val reviewItem = createReviewContentItem(review)
    return VerticalListPanel(0).apply {
      add(loadingPanel)
      add(threadsPanel)
      add(reviewItem)
    }
  }

  private fun createThreadItem(thread: GHPRReviewThreadModel): JComponent {
    val coroutineScopeProvider = ActivatableCoroutineScopeProvider()

    val firstComment: GHPRReviewCommentModel = (if (thread.size <= 0) null else thread.getElementAt(0))
                                               ?: return JPanel(null)

    val tagsPanel = createThreadTagsPanel(thread)

    val bodyPanel = Wrapper()
    val textFlow = MutableStateFlow<@Nls String>("")
    firstComment.addAndInvokeChangesListener { textFlow.value = firstComment.body }

    val panelHandle = GHEditableHtmlPaneHandle(project, bodyPanel, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH,
                                               firstComment::body) { newText ->
      reviewDataProvider.updateComment(EmptyProgressIndicator(), firstComment.id, newText)
        .successOnEdt { firstComment.update(it) }
    }

    val actionsPanel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      if (firstComment.canBeUpdated) add(CodeReviewCommentUIUtil.createEditButton {
        panelHandle.showAndFocusEditor()
      })
      if (firstComment.canBeDeleted) add(CodeReviewCommentUIUtil.createDeleteCommentIconButton {
        reviewDataProvider.deleteComment(EmptyProgressIndicator(), firstComment.id)
      })
    }

    val diff = GHPRReviewThreadComponent.createThreadDiff(project, thread, selectInToolWindowHelper)

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

    val commentComponentFactory = GHPRReviewCommentComponent.factory(project, thread, ghostUser,
                                                                     reviewDataProvider, avatarIconsProvider,
                                                                     suggestedChangeHelper,
                                                                     CodeReviewChatItemUIUtil.ComponentType.FULL_SECONDARY,
                                                                     false,
                                                                     CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)


    val commentsListPanel = ComponentListPanelFactory.createVertical(thread.repliesModel, componentFactory = commentComponentFactory)

    val commentsPanel = if (reviewDataProvider.canComment()) {
      val actionsComponent = GHPRReviewThreadComponent
        .createUncollapsedThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser) {}.let {
          CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
        }.apply {
          border = JBUI.Borders.empty(CodeReviewChatItemUIUtil.ComponentType.FULL_SECONDARY.inputPaddingInsets)
        }

      VerticalListPanel().apply {
        add(commentsListPanel)
        add(actionsComponent)

        focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
          override fun getDefaultComponent(aContainer: Container?): Component? {
            return if (aContainer == this@apply) {
              IdeFocusManager.findInstanceByComponent(aContainer).getFocusTargetFor(actionsComponent)
            }
            else {
              super.getDefaultComponent(aContainer)
            }
          }
        }
      }
    }
    else {
      commentsListPanel
    }

    val collapsedThreadActionsComponent = GHPRReviewThreadComponent
      .getCollapsedThreadActionsComponent(reviewDataProvider, avatarIconsProvider, thread, ghostUser) {
        repliesCollapsedState.update { !it }
        invokeLater {
          CollaborationToolsUIUtil.focusPanel(commentsPanel)
        }
      }.apply {
        border = JBUI.Borders.empty(Thread.Replies.ActionsFolded.VERTICAL_PADDING, 0)
      }

    coroutineScopeProvider.launchInScope {
      repliesCollapsedState.collect {
        collapsedThreadActionsComponent.isVisible = it
        commentsPanel.isVisible = !it
      }
    }

    val diffAndText = VerticalListPanel(Thread.DIFF_TEXT_GAP).apply {
      coroutineScopeProvider.launchInScope {
        combineAndCollect(thread.collapsedState, textFlow) { collapsed, text ->
          removeAll()
          if (collapsed) {
            val textPane = HtmlEditorPane(text).apply {
              foreground = UIUtil.getContextHelpForeground()
            }.let { pane ->
              CollaborationToolsUIUtil
                .wrapWithLimitedSize(pane, DimensionRestrictions.LinesHeight(pane, 2, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH))
            }

            bodyPanel.setContent(textPane)
            add(panelHandle.panel)
            add(diff)
          }
          else {
            val commentComponent = GHPRReviewCommentComponent
              .createCommentBodyComponent(project, suggestedChangeHelper, thread, text, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
            bodyPanel.setContent(commentComponent)
            add(diff)
            add(panelHandle.panel)
          }
          revalidate()
          repaint()
        }
      }
    }
    val content = VerticalListPanel().apply {
      add(diffAndText)
      add(collapsedThreadActionsComponent)
    }

    val actor = firstComment.author ?: ghostUser
    val titlePanel = createTitlePane(actor, firstComment.dateCreated, tagsPanel)
    val mainItem = buildTimelineItem(avatarIconsProvider, actor, content) {
      withHeader(titlePanel, actionsPanel)
      maxContentWidth = null
    }.also {
      coroutineScopeProvider.activateWith(it)
    }

    return VerticalListPanel().apply {
      add(mainItem)
      add(commentsPanel)
    }
  }

  private fun createThreadTagsPanel(thread: GHPRReviewThreadModel): JPanel {
    val outdatedLabel = CollaborationToolsUIUtil.createTagLabel(GithubBundle.message("pull.request.review.thread.outdated"))
    val resolvedLabel = CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag"))
    val pendingLabel = CollaborationToolsUIUtil.createTagLabel(GithubBundle.message("pull.request.review.comment.pending"))

    val tagsPanel = HorizontalListPanel(Title.HORIZONTAL_GAP).apply {
      isOpaque = false
      add(outdatedLabel)
      add(resolvedLabel)
      add(pendingLabel)
    }

    val firstComment = thread.getElementAt(0)
    firstComment.addAndInvokeChangesListener {
      pendingLabel.isVisible = firstComment.state == GHPullRequestReviewCommentState.PENDING
    }

    thread.addAndInvokeStateChangeListener {
      outdatedLabel.isVisible = thread.isOutdated
      resolvedLabel.isVisible = thread.isResolved
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

    val actionsPanel = HorizontalListPanel(8).apply {
      if (panelHandle != null && review.viewerCanUpdate) add(CodeReviewCommentUIUtil.createEditButton {
        panelHandle.showAndFocusEditor()
      })
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
          .minWidth("0"))
      }

      add(StatusMessageComponentFactory.create(HtmlEditorPane(stateText), stateType), CC().grow().push()
        .minWidth("0"))
    }

    return createTimelineItem(avatarIconsProvider, review.author ?: ghostUser, review.createdAt, contentPanel, actionsPanel)
  }

  companion object {
    private val LOG = logger<GHPRTimelineItemComponentFactory>()

    private const val COMMIT_HREF_PREFIX = "commit://"
  }
}