// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCommitsViewModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent

internal object GHPRDetailsCommitInfoComponentFactory {
  private const val COMPONENTS_GAP = 8

  fun create(scope: CoroutineScope, commitsVm: GHPRCommitsViewModel): JComponent {
    val title = HtmlEditorPane().apply {
      bindText(scope, commitsVm.selectedCommit.map { commit -> commit?.messageHeadlineHTML.orEmpty() })
    }
    val description = HtmlEditorPane().apply {
      bindText(scope, commitsVm.selectedCommit.map { commit -> commit?.messageBodyHTML.orEmpty() })
    }
    val info = HtmlEditorPane().apply {
      bindText(scope, commitsVm.selectedCommit.map { commit ->
        commit ?: return@map ""
        val author = commit.author?.user ?: commitsVm.ghostUser
        "${author.shortName} ${DateFormatUtil.formatPrettyDateTime(commit.committedDate)}"
      })
    }

    return VerticalListPanel(COMPONENTS_GAP).apply {
      name = "Commit details info"
      bindVisibility(scope, commitsVm.selectedCommit.map { commit -> commit != null })

      add(title)
      add(description)
      add(info)
    }
  }
}