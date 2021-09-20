// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import git4idea.i18n.GitBundle
import javax.swing.JComponent

class GitStashEditorDiffPreview(diffProcessor: GitStashDiffPreview, tree: ChangesTree, targetComponent: JComponent)
  : SimpleTreeEditorDiffPreview(diffProcessor, tree, targetComponent, false) {

  override fun returnFocusToTree() {
    ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME)?.activate(null)
  }

  override fun getCurrentName(): String {
    return changeViewProcessor.currentChangeName?.let { changeName -> GitBundle.message("stash.editor.diff.preview.title", changeName) }
           ?: GitBundle.message("stash.editor.diff.preview.empty.title")
  }
}
