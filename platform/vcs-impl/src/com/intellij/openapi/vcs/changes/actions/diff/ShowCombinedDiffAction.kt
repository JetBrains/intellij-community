// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.combined.CombinedDiffVirtualFileImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change

internal class ShowCombinedDiffAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val changes = e.getData(VcsDataKeys.CHANGES)
    val project = e.project

    e.presentation.isEnabledAndVisible = CombinedDiffRegistry.isEnabled() &&
                                         project != null && changes != null && changes.size > 1
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val changes = e.getData(VcsDataKeys.CHANGES) ?: return

    showDiff(project, changes.toList())
  }

  companion object {
    fun showDiff(project: Project, changes: List<Change>) {
      val producers: List<CombinedBlockProducer> = changes.mapNotNull {
        val producer = ChangeDiffRequestProducer.create(project, it) ?: return@mapNotNull null
        val id = CombinedPathBlockId(producer.filePath, it.fileStatus)
        CombinedBlockProducer(id, producer)
      }

      val allInOneDiffFile = CombinedDiffVirtualFileImpl(project, producers, VcsBundle.message("changes.combined.diff"))

      DiffEditorTabFilesManager.getInstance(project).showDiffFile(allInOneDiffFile, true)
    }
  }
}
