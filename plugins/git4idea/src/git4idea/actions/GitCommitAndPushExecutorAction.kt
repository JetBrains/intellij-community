// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package git4idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import git4idea.checkin.GitCommitAndPushExecutor

class GitCommitAndPushExecutorAction : BaseCommitExecutorAction() {
  override fun update(e: AnActionEvent) {
    // update presentation before synchronizing its state with button
    e.presentation.text = e.useAmendPrefixIfNeeded(templatePresentation.textWithMnemonic)

    super.update(e)
  }

  override val executorId: String = GitCommitAndPushExecutor.ID
}