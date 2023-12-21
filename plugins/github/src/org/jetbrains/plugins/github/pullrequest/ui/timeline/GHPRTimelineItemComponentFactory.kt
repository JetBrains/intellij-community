// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineThreadComponentFactory
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

class GHPRTimelineItemComponentFactory(private val timelineVm: GHPRTimelineViewModel)
  : (CoroutineScope, GHPRTimelineItem) -> JComponent {

  private val avatarIconsProvider: GHAvatarIconsProvider = timelineVm.avatarIconsProvider
  private val htmlImageLoader = timelineVm.htmlImageLoader
  private val ghostUser: GHUser = timelineVm.ghostUser
  private val prAuthor: GHActor? = timelineVm.detailsVm.details.value.result?.getOrNull()?.author

  private val eventComponentFactory = GHPRTimelineEventComponentFactoryImpl(htmlImageLoader, avatarIconsProvider, ghostUser)

  override fun invoke(cs: CoroutineScope, item: GHPRTimelineItem): JComponent = when (item) {
    is GHPRTimelineItem.Review -> cs.createComponent(item)
    is GHPRTimelineItem.Comment -> cs.createComponent(item)
    is GHPRTimelineItem.Commits -> createComponent(item.commits)
    is GHPRTimelineItem.Event -> try {
      eventComponentFactory.createComponent(item.event)
    }
    catch (e: Exception) {
      val body = GithubBundle.message("cannot.display.item", e.message)
      createTimelineItem(avatarIconsProvider, prAuthor ?: ghostUser, null, SimpleHtmlPane(body))
    }
    is GHPRTimelineItem.Unknown -> {
      val body = GithubBundle.message("cannot.display.item", item.typename)
      createTimelineItem(avatarIconsProvider, prAuthor ?: ghostUser, null, SimpleHtmlPane(body))
    }
  }

  private fun createComponent(commits: List<GHPullRequestCommitShort>): JComponent {
    val commitsPanels = commits.asSequence()
      .map { it.commit }
      .map {
        val builder = HtmlBuilder()
          .append(HtmlChunk.p()
                    .children(
                      HtmlChunk.link("$COMMIT_HREF_PREFIX${it.oid}", it.abbreviatedOid),
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
              append(DateFormatUtil.formatPrettyDateTime(date))
            }
          }
          builder.append(chunk)
        }
        builder.toString()
      }.map { text ->
        SimpleHtmlPane(addBrowserListener = false).apply {
          setHtmlBody(text)
          onHyperlinkActivated {
            val href = it.description
            if (href.startsWith(COMMIT_HREF_PREFIX)) {
              timelineVm.showCommit(href.removePrefix(COMMIT_HREF_PREFIX))
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

      add(SimpleHtmlPane(titleText))
      add(StatusMessageComponentFactory.create(commitsPanels))
    }
    val actor = commits.singleOrNull()?.commit?.author?.user ?: prAuthor ?: ghostUser
    return createTimelineItem(avatarIconsProvider, actor, commits.singleOrNull()?.commit?.author?.date, contentPanel)
  }

  private fun CoroutineScope.createComponent(comment: GHPRTimelineCommentViewModel): JComponent {
    val cs = this@createComponent
    val textPane = SimpleHtmlPane(customImageLoader = htmlImageLoader).apply {
      bindTextIn(cs, comment.bodyHtml)
    }
    val content = EditableComponentFactory.create(cs, textPane, comment.editVm) {
      val actions = createEditActionsConfig(it)
      val editor = CodeReviewCommentTextFieldFactory.createIn(this, it, actions)
      it.requestFocus()
      editor
    }
    val actionsPanel = HorizontalListPanel(8).apply {
      if (comment.canEdit) {
        add(CodeReviewCommentUIUtil.createEditButton {
          comment.editBody()
        }.apply {
          bindDisabledIn(cs, comment.isBusy)
        })
      }
      if (comment.canDelete) {
        add(CodeReviewCommentUIUtil.createDeleteCommentIconButton {
          comment.delete()
        })
      }
    }
    return createTimelineItem(avatarIconsProvider, comment.author, comment.createdAt, content, actionsPanel)
  }

  private fun CoroutineScope.createComponent(review: GHPRTimelineReviewViewModel): JComponent {
    val cs = this@createComponent
    val textPane = SimpleHtmlPane(customImageLoader = htmlImageLoader).apply {
      bindTextIn(cs, review.bodyHtml)
    }
    val content = EditableComponentFactory.wrapTextComponent(cs, textPane, review.editVm)
    val contentPanel = VerticalListPanel().apply {
      add(content)
      add(createReviewStatus(review))
    }
    val actionsPanel = HorizontalListPanel(8).apply {
      if (review.canEdit) add(CodeReviewCommentUIUtil.createEditButton {
        review.editBody()
      })
    }
    val reviewItem = createTimelineItem(avatarIconsProvider, review.author, review.createdAt, contentPanel, actionsPanel)

    return VerticalListPanel(0).apply {
      add(reviewItem)
      bindChildIn(cs, review.threads, index = 0) {
        val threads = it.getOrNull()
        if (threads == null) {
          LoadingTextLabel().let {
            CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
          }.apply {
            border = CodeReviewTimelineUIUtil.ITEM_BORDER
          }
        }
        else {
          ComponentListPanelFactory.createVertical(this, flowOf(threads), componentFactory = {
            GHPRTimelineThreadComponentFactory.createIn(this, it)
          })
        }
      }
    }
  }

  private fun createReviewStatus(review: GHPRTimelineReviewViewModel): JComponent {
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
    return StatusMessageComponentFactory.create(SimpleHtmlPane(stateText), stateType)
  }

  companion object {
    private const val COMMIT_HREF_PREFIX = "commit://"
  }
}
