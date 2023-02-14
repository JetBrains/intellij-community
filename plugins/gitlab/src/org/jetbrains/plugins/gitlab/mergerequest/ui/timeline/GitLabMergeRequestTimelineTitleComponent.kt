// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.codereview.BaseHtmlEditorPane
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.util.bindText
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
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// TODO: extract common code with GitHub (see org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent)
internal object GitLabMergeRequestTimelineTitleComponent {
  fun create(scope: CoroutineScope, mr: GitLabMergeRequest): JComponent {
    val titleLabel = BaseHtmlEditorPane().apply {
      font = JBFont.h2().asBold()
      bindText(scope, mr.title.map { title ->
        createTitleText(title, mr.number, mr.url)
      })
    }
    val mergeRequestStateLabel = JLabel().apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
      bindText(scope, mr.requestState.map { requestState ->
        ReviewDetailsUIUtil.getRequestStateText(requestState)
      })
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        bindVisibility(scope, mr.requestState.map { mergeState ->
          mergeState == RequestState.CLOSED || mergeState == RequestState.MERGED || mergeState == RequestState.DRAFT
        })
        add(it)
      }
    }

    return JPanel(MigLayout(LC().emptyBorders().fillX())).apply {
      isOpaque = false
      add(titleLabel, CC().grow().push())
      add(mergeRequestStateLabel, CC())
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