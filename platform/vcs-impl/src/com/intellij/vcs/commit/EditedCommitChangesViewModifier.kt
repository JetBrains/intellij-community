// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.platform.vcs.impl.shared.commit.insertEditedCommitNode

internal class EditedCommitChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    val editedCommit = ChangesViewWorkflowManager.getInstance(project).editedCommit.value ?: return
    insertEditedCommitNode(builder, editedCommit)
  }
}
