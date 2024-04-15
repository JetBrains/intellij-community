// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.openapi.wm.IdeFocusManager
import java.awt.Component
import java.lang.ref.WeakReference

class SavedPatchesEditorDiffPreview(
  private val changesBrowser: SavedPatchesChangesBrowser,
  private val focusMainComponent: (Component?) -> Unit,
) : TreeHandlerEditorDiffPreview(changesBrowser.viewer, SavedPatchesDiffPreviewHandler) {

  private var lastFocusOwner: WeakReference<Component>? = null

  override fun dispose() {
    lastFocusOwner = null
    super.dispose()
  }

  override fun createViewer(): DiffEditorViewer {
    return SavedPatchesDiffProcessor(tree, true)
  }

  override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String {
    val currentPatchObject = changesBrowser.currentPatchObject
    return currentPatchObject?.getDiffPreviewTitle(wrapper?.presentableName)
           ?: VcsBundle.message("saved.patch.editor.diff.preview.empty.title")
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    lastFocusOwner = WeakReference(IdeFocusManager.getInstance(project).focusOwner)
    return super.openPreview(requestFocus)
  }

  override fun returnFocusToTree() {
    val focusOwner = lastFocusOwner?.get()
    lastFocusOwner = null
    focusMainComponent(focusOwner)
  }

  override fun updateDiffAction(event: AnActionEvent) {
    event.presentation.isVisible = true
    event.presentation.isEnabled = event.getData(SavedPatchesUi.SAVED_PATCH_CHANGES)?.any() == true
  }
}
