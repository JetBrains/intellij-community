// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile

class GHPROpenPullRequestTimelineAction
  : DumbAwareAction(GithubBundle.messagePointer("pull.request.view.conversations.action"),
                    GithubBundle.messagePointer("pull.request.view.conversations.action.description"),
                    null) {

  override fun update(e: AnActionEvent) {
    val dataContext = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    val actionDataContext = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    e.presentation.isEnabled = e.project != null && dataContext != null && actionDataContext != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val dataContext = e.getRequiredData(GHPRActionKeys.DATA_CONTEXT)
    val actionDataContext = e.getRequiredData(GHPRActionKeys.ACTION_DATA_CONTEXT)

    val file = GHPRVirtualFile(dataContext, actionDataContext.pullRequestDetails)
    FileEditorManager.getInstance(project).openFile(file, true)
  }
}