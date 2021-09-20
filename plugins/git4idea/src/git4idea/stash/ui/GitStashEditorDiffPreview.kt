// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import javax.swing.JComponent

class GitStashEditorDiffPreview(diffProcessor: GitStashDiffPreview, tree: ChangesTree, targetComponent: JComponent) : EditorTabPreview(diffProcessor) {

  private val stashDiffPreview: GitStashDiffPreview get() = diffProcessor as GitStashDiffPreview

  init {
    escapeHandler = Runnable {
      closePreview()
      ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME)?.activate(null)
    }
    installListeners(tree, false)
    installNextDiffActionOn(targetComponent)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  override fun updateAvailability(event: AnActionEvent) {
    event.presentation.isVisible = event.isFromActionToolbar || event.presentation.isEnabled
  }

  override fun getCurrentName(): String {
    return stashDiffPreview
             .currentChangeName?.let { changeName -> GitBundle.message("stash.editor.diff.preview.title", changeName) }
           ?: GitBundle.message("stash.editor.diff.preview.empty.title")
  }

  override fun hasContent(): Boolean {
    return stashDiffPreview.currentChangeName != null
  }
}
