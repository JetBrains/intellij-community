// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder

class EditedCommitChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    val workflowHandler = ChangesViewManager.getInstanceEx(project).commitWorkflowHandler ?: return
    val editedCommit = workflowHandler.ui.editedCommit ?: return

    val commitNode = EditedCommitNode(editedCommit)
    builder.insertSubtreeRoot(commitNode)
    builder.insertChanges(editedCommit.commit.changes, commitNode)
  }
}