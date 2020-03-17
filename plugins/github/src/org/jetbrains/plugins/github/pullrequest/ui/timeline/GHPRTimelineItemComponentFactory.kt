// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.ui.components.panels.VerticalBox
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState.*
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRCommentsUIUtil
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadCommentsPanel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.*
import javax.swing.*
import kotlin.math.ceil
import kotlin.math.floor

class GHPRTimelineItemComponentFactory(private val project: Project,
                                       private val reviewService: GHPRReviewServiceAdapter,
                                       private val avatarIconsProvider: GHAvatarIconsProvider,
                                       private val reviewsThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                       private val reviewDiffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                                       private val eventComponentFactory: GHPRTimelineEventComponentFactory<GHPRTimelineEvent>,
                                       private val currentUser: GHUser) {

  fun createComponent(item: GHPRTimelineItem): Item {
    try {
      return when (item) {
        is GHPullRequestCommit -> Item(AllIcons.Vcs.CommitNode, commitTitle(item.commit))

        is GHIssueComment -> createComponent(item)
        is GHPullRequestReview -> createComponent(item)

        is GHPRTimelineEvent -> eventComponentFactory.createComponent(item)
        else -> throw IllegalStateException("Unknown item type")
      }
    }
    catch (e: Exception) {
      return Item(AllIcons.General.Warning, HtmlEditorPane("Cannot display item - ${e.message}"))
    }
  }

  private fun createComponent(model: GHIssueComment) =
    Item(userAvatar(model.author),
         actionTitle(model.author, "commented", model.createdAt),
         HtmlEditorPane(model.bodyHtml))

  private fun createComponent(review: GHPullRequestReview): Item {
    val reviewThreadsModel = reviewsThreadsModelsProvider.getReviewThreadsModel(review.id)

    val reviewPanel = VerticalBox().apply {
      add(Box.createRigidArea(JBDimension(0, 4)))
      if (review.bodyHTML.isNotEmpty()) {
        add(HtmlEditorPane(review.bodyHTML).apply {
          border = JBUI.Borders.emptyBottom(12)
        })
      }
      add(GHPRReviewThreadsPanel(reviewThreadsModel, ::createReviewThread))
    }

    val icon = when (review.state) {
      APPROVED -> GithubIcons.ReviewAccepted
      CHANGES_REQUESTED -> GithubIcons.ReviewRejected
      COMMENTED -> GithubIcons.Review
      DISMISSED -> GithubIcons.Review
      PENDING -> GithubIcons.Review
    }

    val actionText = when (review.state) {
      APPROVED -> "approved these changes"
      CHANGES_REQUESTED -> "rejected these changes"
      COMMENTED, DISMISSED, PENDING -> "reviewed"
    }

    return Item(icon, actionTitle(avatarIconsProvider, review.author, actionText, review.createdAt), reviewPanel)
  }

  private fun createReviewThread(thread: GHPRReviewThreadModel): JComponent {
    val panel = VerticalBox().apply {
      isOpaque = false
      add(reviewDiffComponentFactory.createComponent(thread.filePath, thread.diffHunk))
      add(Box.createRigidArea(JBDimension(0, 12)))
      add(GHPRReviewThreadCommentsPanel(thread, avatarIconsProvider))
    }

    if (reviewService.canComment()) {
      panel.add(Box.createRigidArea(JBDimension(0, 12)))
      panel.add(GHPRCommentsUIUtil.createTogglableCommentField(project, avatarIconsProvider, currentUser, "Reply") { text ->
        reviewService.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
          thread.addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl))
        }
      })
    }
    return panel
  }

  private fun userAvatar(user: GHActor?): JLabel {
    return userAvatar(avatarIconsProvider, user)
  }

  private fun userAvatar(user: GHGitActor?): JLabel {
    return LinkLabel<Any>("", avatarIconsProvider.getIcon(user?.avatarUrl), LinkListener { _, _ ->
      user?.url?.let { BrowserUtil.browse(it) }
    })
  }

  private fun commitTitle(commit: GHCommit): JComponent {
    //language=HTML
    val text = """${commit.messageHeadlineHTML} <a href='${commit.url}'>${commit.abbreviatedOid}</a>"""

    return HorizontalBox().apply {
      add(userAvatar(commit.author))
      add(Box.createRigidArea(JBDimension(8, 0)))
      add(HtmlEditorPane(text))
    }
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
      add(title, CC().growX().pushX())
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
