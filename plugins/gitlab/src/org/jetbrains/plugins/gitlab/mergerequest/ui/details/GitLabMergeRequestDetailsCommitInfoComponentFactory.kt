// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsCommitsViewModel
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsCommitInfoComponentFactory {
  private const val GAP = 8

  fun create(scope: CoroutineScope, commitsVm: GitLabMergeRequestDetailsCommitsViewModel): JComponent {
    val title = SimpleHtmlPane().apply {
      bindTextHtml(scope, commitsVm.selectedCommit.map { commit -> commit?.title.orEmpty() })
    }
    val description = SimpleHtmlPane().apply {
      bindTextHtml(scope, commitsVm.selectedCommit.map { commit -> commit?.description.orEmpty() })
    }

    return VerticalListPanel(GAP).apply {
      isOpaque = false
      name = "Commit details info"
      bindVisibility(scope, commitsVm.selectedCommit.map { commit -> commit != null })

      add(title)
      add(description)
    }
  }
}