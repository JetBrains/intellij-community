// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile

class GHPROpenPullRequestAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.open.action"),
                                                  GithubBundle.messagePointer("pull.request.open.action.description"),
                                                  null) {

  override fun update(e: AnActionEvent) {
    val dataContext = e.getData(GHPRActionKeys.DATA_CONTEXT)
    val viewPullRequestExecutor = e.getData(GHPRActionKeys.VIEW_PULL_REQUEST_EXECUTOR)
    val selection = e.getData(GHPRActionKeys.SELECTED_PULL_REQUEST)
    e.presentation.isEnabled = e.project != null && dataContext != null && selection != null && viewPullRequestExecutor != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val dataContext = e.getRequiredData(GHPRActionKeys.DATA_CONTEXT)
    val selection = e.getRequiredData(GHPRActionKeys.SELECTED_PULL_REQUEST)
    val viewPullRequestExecutor = e.getRequiredData(GHPRActionKeys.VIEW_PULL_REQUEST_EXECUTOR)

    viewPullRequestExecutor.accept(selection)
    val file = GHPRVirtualFile(dataContext, selection)
    FileEditorManager.getInstance(project).openFile(file, false)
  }
}