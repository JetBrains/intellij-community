// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.SingleValueModel
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GHPRTitleComponent {

  fun create(model: SingleValueModel<GHPullRequestShort>): JComponent {
    val titlePane = HtmlEditorPane().apply {
      font = JBFont.h2().asBold()
    }
    model.addAndInvokeListener {
      titlePane.setBody(createTitleText(model.value.title, model.value.number.toString(), model.value.url))
    }
    return titlePane
  }

  fun create(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    val titleLabel = HtmlEditorPane().apply {
      font = JBFont.h2().asBold()
      bindText(scope, reviewDetailsVm.titleState.map { title ->
        createTitleText(title, reviewDetailsVm.number, reviewDetailsVm.url)
      })
    }
    val pullRequestStateLabel = JLabel().apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
      bindText(scope, reviewDetailsVm.requestState.map { requestState ->
        ReviewDetailsUIUtil.getRequestStateText(requestState)
      })
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        bindVisibility(scope, reviewDetailsVm.requestState.map { mergeState ->
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
          .link(url, "#${reviewNumber}")
          .wrapWith(HtmlChunk.font(ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())))
      )
      .wrapWithHtmlBody()
      .toString()
  }
}