// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabOpenMergeRequestInBrowserAction
  : DumbAwareAction(GitLabBundle.messagePointer("merge.request.open.in.browser.action"),
                    GitLabBundle.messagePointer("merge.request.open.in.browser.action.description"),
                    null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabledAndVisible = selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selection = e.getRequiredData(GitLabMergeRequestsActionKeys.SELECTED)
    BrowserUtil.browse(selection.webUrl)
  }
}