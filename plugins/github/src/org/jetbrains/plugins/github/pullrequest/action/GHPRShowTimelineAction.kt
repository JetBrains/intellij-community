// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile

class GHPRShowTimelineAction : DumbAwareAction("View Timeline", "Open pull request timeline in editor tab", null) {
  override fun update(e: AnActionEvent) {
    val selection = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    val context = e.getData(GithubPullRequestKeys.ACTION_DATA_CONTEXT)
    e.presentation.isEnabled = e.project != null && selection != null && context != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(PlatformDataKeys.PROJECT)
    val selection = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    val context = e.getRequiredData(GithubPullRequestKeys.ACTION_DATA_CONTEXT)

    val file = GHPRVirtualFile(context, selection)
    FileEditorManager.getInstance(project).openFile(file, true)
  }
}