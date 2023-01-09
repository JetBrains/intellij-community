// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.IdeFocusManager
import git4idea.index.GitStageContentProvider
import git4idea.index.isStagingAreaAvailable
import git4idea.index.showStagingArea

class GitCommitWithStagingAreaAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible =
      isStagingAreaAvailable(project) &&
      ChangesViewContentManager.getToolWindowFor(project, GitStageContentProvider.STAGING_AREA_TAB_NAME) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    showStagingArea(project) { gitStagePanel ->
      IdeFocusManager.getInstance(project).requestFocus(gitStagePanel.commitMessage.editorField, false).doWhenDone {
        gitStagePanel.commitMessage.editorField.selectAll()
      }
    }
  }
}