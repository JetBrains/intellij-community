// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf.diff

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry.Companion.get
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.vcs.impl.backend.shelf.ShelfTree
import kotlinx.coroutines.CoroutineScope

class BackendShelveEditorDiffPreview(tree: ShelfTree, private  val cs: CoroutineScope) : TreeHandlerEditorDiffPreview(tree, tree, ShelveTreeDiffPreviewHandler(cs)) {
  override fun createViewer(): DiffEditorViewer {
    return ShelvedPreviewProcessor(project, cs, tree as ShelfTree, true)
  }

  public override fun returnFocusToTree() {
    ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.SHELF)?.activate(null)
  }

  override fun updateDiffAction(event: AnActionEvent) {
    DiffShelvedChangesActionProvider.updateAvailability(event)
  }

  override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String {
    return if (wrapper != null)
      VcsBundle.message("shelve.editor.diff.preview.title", wrapper.getPresentableName())
    else
      VcsBundle.message("shelved.version.name")
  }

  override fun isOpenPreviewWithSingleClickEnabled(): Boolean {
    return get("show.diff.preview.as.editor.tab.with.single.click").asBoolean()
  }
}
