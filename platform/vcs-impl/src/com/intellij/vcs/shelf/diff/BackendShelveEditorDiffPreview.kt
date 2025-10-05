// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendShelveEditorDiffPreview(project: Project, private val cs: CoroutineScope, private val changesProvider: ShelfDiffChangesProvider) : EditorTabDiffPreview(project) {
  override fun hasContent(): Boolean {
    return changesProvider.getAllChanges().isNotEmpty
  }

  override fun createViewer(): DiffEditorViewer {
    return ShelvedPreviewProcessor(project, cs, true, changesProvider)
  }

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer> {
    val producers = if (selectedOnly) changesProvider.getSelectedChanges() else changesProvider.getAllChanges()
    return ListSelection.create(producers.toList(), null)
      .withExplicitSelection(selectedOnly)
      .map { it.createProducer(project) }
  }

  override fun getEditorTabName(processor: DiffEditorViewer?): String {
    val wrapper = (processor as? ShelvedPreviewProcessor)?.currentChange
    return if (wrapper != null)
      VcsBundle.message("shelve.editor.diff.preview.title", wrapper.getPresentableName())
    else
      return VcsBundle.message("shelved.version.name")
  }

  override fun updateDiffAction(event: AnActionEvent) {
    DiffShelvedChangesActionProvider.updateAvailability(event)
  }
}
