// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.actions.commit.AbstractCommitChangesAction
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor

class CreatePatchAction : AbstractCommitChangesAction() {
  override fun getExecutor(project: Project): CommitExecutor = CreatePatchCommitExecutor(project)

  override fun isActionEnabled(manager: ChangeListManager, it: Change): Boolean {
    return super.isActionEnabled(manager, it) || it.fileStatus != FileStatus.HIJACKED
  }
}