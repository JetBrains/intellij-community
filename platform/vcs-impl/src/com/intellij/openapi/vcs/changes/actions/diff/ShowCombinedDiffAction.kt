// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change

class ShowCombinedDiffAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val changes = e.getData(VcsDataKeys.CHANGES)
    val project = e.project

    e.presentation.isEnabledAndVisible = project != null && changes != null && changes.size > 1
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changes = e.getRequiredData(VcsDataKeys.CHANGES)

    showDiff(project, changes.toList())
  }

  companion object {
    fun showDiff(project: Project, changes: List<Change>) {
      val producers = changes.mapNotNull { ChangeDiffRequestProducer.create(project, it) }
      val allInOneDiffFile = CombinedChangeDiffVirtualFile(CombinedChangeDiffRequestProducer(project, producers))

      DiffEditorTabFilesManager.getInstance(project).showDiffFile(allInOneDiffFile, true)
    }
  }
}
