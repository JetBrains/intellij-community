// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder

internal class EditedCommitChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    val workflowHandler = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler ?: return
    val editedCommit = workflowHandler.ui.editedCommit ?: return

    insertEditedCommitNode(builder, editedCommit)
  }
}
