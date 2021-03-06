// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

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
    openWithDoubleClick(tree)
    installNextDiffActionOn(targetComponent)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  override fun getCurrentName(): String {
    return GitBundle.message("stash.editor.diff.preview.title", stashDiffPreview.currentChangeName)
  }

  override fun hasContent(): Boolean {
    return stashDiffPreview.currentChangeName != null
  }
}