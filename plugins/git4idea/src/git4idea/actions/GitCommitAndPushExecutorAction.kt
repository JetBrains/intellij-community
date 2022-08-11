// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package git4idea.actions

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import git4idea.checkin.GitCommitAndPushExecutor

class GitCommitAndPushExecutorAction : BaseCommitExecutorAction() {
  init {
    templatePresentation.setText(DvcsBundle.messagePointer("action.commit.and.push.text"))
  }

  override val executorId: String = GitCommitAndPushExecutor.ID
}