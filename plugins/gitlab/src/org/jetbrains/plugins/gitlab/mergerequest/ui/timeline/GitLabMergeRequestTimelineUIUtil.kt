// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.util.bindText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.util.*
import javax.swing.JComponent

object GitLabMergeRequestTimelineUIUtil {

  fun createTitleTextPane(author: GitLabUserDTO, date: Date): JComponent {
    val titleText = getTitleHtml(author, date)
    val titleTextPane = SimpleHtmlPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
  }

  fun createTitleTextPane(cs: CoroutineScope, author: Flow<GitLabUserDTO>, date: Date): JComponent =
    SimpleHtmlPane().apply {
      foreground = UIUtil.getContextHelpForeground()
    }.apply {
      bindText(cs, author.map { getTitleHtml(it, date) })
    }

  private fun getTitleHtml(author: GitLabUserDTO, date: Date): @NlsSafe String {
    val userNameLink = HtmlChunk.link(author.webUrl, author.name)
      .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getLabelForeground())))
      .bold()
    return HtmlBuilder()
      .append(userNameLink)
      .append(HtmlChunk.nbsp())
      .append(JBDateFormat.getFormatter().formatPrettyDateTime(date))
      .toString()
  }
}