// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor

class CreatePatchAction : AbstractCommitChangesAction() {
  override fun getExecutor(project: Project): CommitExecutor? = CreatePatchCommitExecutor.getInstance(project)
}