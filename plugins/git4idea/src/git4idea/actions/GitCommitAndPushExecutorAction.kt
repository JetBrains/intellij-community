// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package git4idea.actions

import com.intellij.dvcs.commit.getCommitAndPushActionName
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import git4idea.checkin.GitCommitAndPushExecutor

class GitCommitAndPushExecutorAction : BaseCommitExecutorAction() {
  init {
    templatePresentation.setText(DvcsBundle.messagePointer("action.commit.and.push.text"))
  }

  override fun update(e: AnActionEvent) {
    // update presentation before synchronizing its state with button
    val workflowHandler = e.getData(COMMIT_WORKFLOW_HANDLER)
    if (workflowHandler != null) {
      e.presentation.text = workflowHandler.getCommitAndPushActionName()
    }

    super.update(e)
  }

  override val executorId: String = GitCommitAndPushExecutor.ID
}