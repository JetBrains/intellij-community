// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.action

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestCloseCreateTabAction(
  private val project: Project
) : AbstractAction(GitLabBundle.message("merge.request.create.action.close.tab.text")) {
  override fun actionPerformed(e: ActionEvent?) {
    project.service<GitLabToolWindowViewModel>().activateAndAwaitProject {
      closeTab(GitLabReviewTab.NewMergeRequest)
    }
  }
}