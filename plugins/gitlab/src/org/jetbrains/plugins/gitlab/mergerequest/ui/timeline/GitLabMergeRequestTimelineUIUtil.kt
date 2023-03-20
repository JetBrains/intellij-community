// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.util.bindChild
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import java.util.*
import javax.swing.JComponent

object GitLabMergeRequestTimelineUIUtil {

  fun createTitleTextPane(author: GitLabUserDTO, date: Date?): JComponent {
    val titleText = getTitleHtml(author, date)
    val titleTextPane = SimpleHtmlPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
  }

  fun createNoteTitleComponent(cs: CoroutineScope, noteVm: GitLabNoteViewModel): JComponent {
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(createTitleTextPane(noteVm.author, noteVm.createdAt))

      noteVm.resolveVm?.resolved?.let { resolvedFlow ->
        bindChild(cs, resolvedFlow) { _, resolved ->
          if (resolved) {
            CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag"))
          }
          else {
            null
          }
        }
      }
    }
  }

  private fun getTitleHtml(author: GitLabUserDTO, date: Date?): @NlsSafe String {
    val userNameLink = HtmlChunk.link(author.webUrl, author.name)
      .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getLabelForeground())))
      .bold()
    val builder = HtmlBuilder()
      .append(userNameLink)
    if (date != null) {
      builder
        .append(HtmlChunk.nbsp())
        .append(JBDateFormat.getFormatter().formatPrettyDateTime(date))
    }

    return builder.toString()
  }
}