// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import com.intellij.openapi.wm.IdeFocusManager
import java.awt.Component
import javax.swing.JComponent

abstract class SavedPatchesEditorDiffPreview(diffProcessor: SavedPatchesDiffPreview, tree: ChangesTree, targetComponent: JComponent,
                                             private val focusMainComponent: (Component?) -> Unit)
  : SimpleTreeEditorDiffPreview(diffProcessor, tree, targetComponent, false) {
  private var lastFocusOwner: Component? = null

  init {
    Disposer.register(diffProcessor, Disposable { lastFocusOwner = null })
  }

  override fun openPreview(focusEditor: Boolean): Boolean {
    lastFocusOwner = IdeFocusManager.getInstance(project).focusOwner
    return super.openPreview(focusEditor)
  }

  override fun returnFocusToTree() {
    val focusOwner = lastFocusOwner
    lastFocusOwner = null
    focusMainComponent(focusOwner)
  }
}
