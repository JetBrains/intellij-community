// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// TODO: extract common code with GitHub (see org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTitleComponent)
internal object GitLabMergeRequestTimelineTitleComponent {
  fun create(scope: CoroutineScope, mr: GitLabMergeRequest): JComponent {
    val titleLabel = SimpleHtmlPane().apply {
      name = "Review timeline title panel"
      font = JBFont.h2().asBold()
      bindTextHtmlIn(scope, mr.title.map { title ->
        CodeReviewTitleUIUtil.createTitleText(
          title = title,
          reviewNumber = "!${mr.number}",
          url = mr.url,
          tooltip = GitLabBundle.message("open.on.gitlab.tooltip")
        )
      })
    }
    val stateLabel = JLabel().apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
      bindTextIn(scope, mr.reviewRequestState.map { reviewRequestState ->
        ReviewDetailsUIUtil.getRequestStateText(reviewRequestState)
      })
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        bindVisibilityIn(scope, mr.reviewRequestState.map { reviewRequestState ->
          reviewRequestState == ReviewRequestState.CLOSED ||
          reviewRequestState == ReviewRequestState.MERGED ||
          reviewRequestState == ReviewRequestState.DRAFT
        })
        add(it)
      }
    }

    return JPanel(MigLayout(
      LC().emptyBorders().fillX().hideMode(3),
      AC().gap("push")
    )).apply {
      isOpaque = false
      add(titleLabel)
      add(stateLabel, CC().alignY("top"))
    }
  }
}