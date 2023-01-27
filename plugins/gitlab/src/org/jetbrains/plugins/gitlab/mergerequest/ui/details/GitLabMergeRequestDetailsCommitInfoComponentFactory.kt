// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsCommitInfoComponentFactory {
  private const val GAP = 8

  fun create(scope: CoroutineScope, commit: Flow<GitLabCommitDTO?>): JComponent {
    val title = SimpleHtmlPane().apply {
      bindTextHtml(scope, commit.map { it?.title.orEmpty() })
    }
    val description = SimpleHtmlPane().apply {
      bindTextHtml(scope, commit.map { it?.description.orEmpty() })
    }

    return VerticalListPanel(GAP).apply {
      isOpaque = false
      name = "Commit details info"
      bindVisibility(scope, commit.map { it != null })

      add(title)
      add(description)
    }
  }
}