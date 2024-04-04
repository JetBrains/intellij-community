// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.addGitLabHyperlinkListener
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

internal object GitLabMergeRequestTimelineTitleComponent {
  fun create(project: Project, scope: CoroutineScope, vm: GitLabMergeRequestViewModel): JComponent {
    return SimpleHtmlPane(addBrowserListener = false).apply {
      name = "Review timeline title panel"
      font = JBFont.h2().asBold()
      bindTextHtmlIn(scope, vm.title.map { title ->
        CodeReviewTitleUIUtil.createTitleText(
          title = title,
          reviewNumber = vm.number,
          url = vm.url,
          tooltip = GitLabBundle.message("open.on.gitlab.tooltip")
        )
      })
      addGitLabHyperlinkListener(project)
    }
  }
}