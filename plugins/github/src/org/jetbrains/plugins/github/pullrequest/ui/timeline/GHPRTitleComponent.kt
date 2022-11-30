// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.util.bindText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent

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

  fun create(scope: CoroutineScope, reviewDetailsVM: GHPRDetailsViewModel): JComponent {
    val titleLabel = HtmlEditorPane().apply {
      font = JBFont.h2().asBold()
      bindText(scope, combine(reviewDetailsVM.titleState, reviewDetailsVM.numberState, reviewDetailsVM.urlState, ::createTitleText))
    }

    return titleLabel
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