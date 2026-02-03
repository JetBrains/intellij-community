// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.ui.actions.history.CompareRevisionsFromFileHistoryActionProvider

internal class FileHistoryEditorDiffPreview(project: Project, private val fileHistoryPanel: FileHistoryPanel)
  : EditorTabDiffPreview(project) {

  override fun getEditorTabName(processor: DiffEditorViewer?): String {
    return VcsLogBundle.message("file.history.diff.preview.editor.tab.name", fileHistoryPanel.filePath.name)
  }

  override fun hasContent(): Boolean {
    return fileHistoryPanel.selectedChange != null
  }

  override fun createViewer(): DiffEditorViewer {
    return fileHistoryPanel.createDiffPreview(true)
  }

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer> {
    val changes = listOfNotNull(fileHistoryPanel.selectedChange)
    return ListSelection.createAt(changes, 0)
      .withExplicitSelection(selectedOnly)
      .map { change -> ChangeDiffRequestProducer.create(project, change) }
  }

  override fun updateDiffAction(event: AnActionEvent) {
    val selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
    CompareRevisionsFromFileHistoryActionProvider.setTextAndDescription(event, selection)
  }
}
