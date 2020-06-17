// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent

internal object GHPRHeaderPanel {

  fun create(model: SingleValueModel<GHPullRequestShort>): JComponent {
    val title = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
      font = font.deriveFont((font.size * 1.5).toFloat())
    }

    val number = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
      font = font.deriveFont((font.size * 1.4).toFloat())
      foreground = UIUtil.getContextHelpForeground()
    }

    model.addAndInvokeValueChangedListener {
      title.icon = when (model.value.state) {
        GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
        GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
        GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
      }
      title.text = model.value.title
      number.text = " #${model.value.number}"
    }

    return BorderLayoutPanel().addToCenter(title).addToRight(number).andTransparent()
  }
}