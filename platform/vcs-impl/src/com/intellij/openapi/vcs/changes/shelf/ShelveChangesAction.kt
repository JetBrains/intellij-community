// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.commit.AbstractCommitChangesAction
import com.intellij.openapi.vcs.changes.CommitExecutor

class ShelveChangesAction : AbstractCommitChangesAction() {
  override fun getExecutor(project: Project): CommitExecutor = ShelveChangesCommitExecutor(project)
}