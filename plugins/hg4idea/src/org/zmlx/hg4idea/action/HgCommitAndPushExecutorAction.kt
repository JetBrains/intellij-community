// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.action

import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutor

class HgCommitAndPushExecutorAction : BaseCommitExecutorAction() {
  override val executorId: String = HgCommitAndPushExecutor.ID
}