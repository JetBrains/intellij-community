// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewUIModel

class GitLabMergeRequestShowFileDiffAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val model = e.getData(CommonDataKeys.EDITOR)?.getUserData(GitLabMergeRequestEditorReviewUIModel.KEY)
    e.presentation.isEnabledAndVisible = model != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val model = e.getRequiredData(CommonDataKeys.EDITOR).getUserData(GitLabMergeRequestEditorReviewUIModel.KEY) ?: return
    val line = e.getData(CommonDataKeys.CARET)?.logicalPosition?.line
    model.showDiff(line)
  }
}