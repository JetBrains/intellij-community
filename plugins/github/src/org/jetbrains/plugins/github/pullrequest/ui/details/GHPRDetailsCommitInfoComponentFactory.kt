// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCommitsViewModel
import javax.swing.JComponent

internal object GHPRDetailsCommitInfoComponentFactory {
  private const val COMPONENTS_GAP = 8

  fun create(scope: CoroutineScope, commitsVm: GHPRCommitsViewModel): JComponent {
    val title = SimpleHtmlPane().apply {
      bindTextHtml(scope, commitsVm.selectedCommit.map { commit -> commit?.messageHeadlineHTML.orEmpty() })
    }
    val info = SimpleHtmlPane().apply {
      bindTextHtml(scope, commitsVm.selectedCommit.map { commit ->
        commit ?: return@map ""
        val author = commit.author?.user ?: commitsVm.ghostUser
        HtmlBuilder()
          .append(HtmlChunk.text("${author.getPresentableName()} ${DateFormatUtil.formatPrettyDateTime(commit.committedDate)}"))
          .wrapWith(HtmlChunk.font(ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())))
          .toString()
      })
    }

    return VerticalListPanel(COMPONENTS_GAP).apply {
      name = "Commit details info"
      bindVisibility(scope, commitsVm.selectedCommit.map { commit -> commit != null })

      add(title)
      add(info)
    }
  }
}