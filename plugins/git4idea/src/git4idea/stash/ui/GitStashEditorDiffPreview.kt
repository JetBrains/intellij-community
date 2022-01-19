// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import com.intellij.openapi.wm.IdeFocusManager
import java.awt.Component
import javax.swing.JComponent

abstract class GitStashEditorDiffPreview(diffProcessor: GitStashDiffPreview, tree: ChangesTree, targetComponent: JComponent)
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
    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME) ?: return

    val focusOwner = lastFocusOwner
    lastFocusOwner = null

    if (focusOwner == null) {
      toolWindow.activate(null)
      return
    }

    toolWindow.activate({
                          IdeFocusManager.getInstance(project).requestFocus(focusOwner, true)
                        }, false)
  }
}
