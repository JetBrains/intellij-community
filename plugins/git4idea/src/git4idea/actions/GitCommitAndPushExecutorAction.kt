// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package git4idea.actions

import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import git4idea.checkin.GitCommitAndPushExecutor

class GitCommitAndPushExecutorAction : BaseCommitExecutorAction() {
  override val executorId: String = GitCommitAndPushExecutor.ID
}