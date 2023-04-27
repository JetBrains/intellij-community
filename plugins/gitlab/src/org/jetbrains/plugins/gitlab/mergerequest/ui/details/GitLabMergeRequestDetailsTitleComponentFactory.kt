// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsTitleComponentFactory {
  fun create(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    val titleLabel = SimpleHtmlPane().apply {
      name = "Review details title panel"
      font = JBFont.h2().asBold()
      bindTextHtml(scope, detailsInfoVm.title.map { title ->
        createTitleText(title, detailsInfoVm.number, detailsInfoVm.url)
      })
    }
    val pullRequestStateLabel = JLabel().apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
      bindText(scope, detailsInfoVm.requestState.map { requestState ->
        ReviewDetailsUIUtil.getRequestStateText(requestState)
      })
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        bindVisibility(scope, detailsInfoVm.requestState.map { mergeState ->
          mergeState == RequestState.CLOSED || mergeState == RequestState.MERGED || mergeState == RequestState.DRAFT
        })
        add(it)
      }
    }

    return JPanel(MigLayout(LC().emptyBorders().fillX())).apply {
      isOpaque = false
      add(titleLabel, CC().grow().push())
      add(pullRequestStateLabel, CC())
    }
  }

  private fun createTitleText(title: @NlsSafe String, reviewNumber: @NlsSafe String, url: String): @NlsSafe String {
    return HtmlBuilder()
      .append(title)
      .nbsp()
      .append(
        HtmlChunk
          .link(url, "!${reviewNumber}")
          .wrapWith(HtmlChunk.font(ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())))
      )
      .wrapWithHtmlBody()
      .toString()
  }
}