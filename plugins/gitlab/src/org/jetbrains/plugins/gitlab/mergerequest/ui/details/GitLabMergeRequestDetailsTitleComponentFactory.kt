// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsTitleComponentFactory {
  fun create(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    return SimpleHtmlPane().apply {
      name = "Review details title panel"
      font = JBFont.h2().asBold()
      bindTextHtml(scope, detailsInfoVm.title.map { title ->
        createTitleText(title, detailsInfoVm.number, detailsInfoVm.url)
      })
    }
  }

  private fun createTitleText(title: @NlsSafe String, reviewNumber: @NlsSafe String, url: String): @NlsSafe String {
    return HtmlBuilder()
      .append(title)
      .nbsp()
      .append(
        HtmlChunk
          .link(url, "#${reviewNumber}")
          .wrapWith(HtmlChunk.font(ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())))
      )
      .wrapWithHtmlBody()
      .toString()
  }
}