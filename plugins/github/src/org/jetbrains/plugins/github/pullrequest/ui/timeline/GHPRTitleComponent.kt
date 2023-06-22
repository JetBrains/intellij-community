// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.util.ui.JBFont
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JComponent

internal object GHPRTitleComponent {

  fun create(model: SingleValueModel<GHPullRequestShort>): JComponent {
    val titlePane = SimpleHtmlPane().apply {
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
}