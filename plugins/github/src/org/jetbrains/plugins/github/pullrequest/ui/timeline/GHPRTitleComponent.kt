// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.ui.PopupHandler
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
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
      val title = CodeReviewTitleUIUtil.createTitleText(
        title = model.value.title,
        reviewNumber = "#${model.value.number}",
        url = model.value.url,
        tooltip = GithubBundle.message("open.on.github.action")
      )
      titlePane.setHtmlBody(title)
    }
    return titlePane
  }

  fun create(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    val titleLabel = HtmlEditorPane().apply {
      font = JBFont.h2().asBold()
      bindTextHtml(scope, reviewDetailsVm.titleState.map { title ->
        CodeReviewTitleUIUtil.createTitleText(
          title = title,
          reviewNumber = "#${reviewDetailsVm.number}",
          url = reviewDetailsVm.url,
          tooltip = GithubBundle.message("open.on.github.action")
        )
      })
      val group = ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup") as ActionGroup
      PopupHandler.installPopupMenu(this, group, "GHPRDetailsPopup")
    }
    val stateLabel = JLabel().apply {
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