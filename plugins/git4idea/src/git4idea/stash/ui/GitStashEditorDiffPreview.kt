// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import javax.swing.JComponent

abstract class GitStashEditorDiffPreview(diffProcessor: GitStashDiffPreview, tree: ChangesTree, targetComponent: JComponent)
  : SimpleTreeEditorDiffPreview(diffProcessor, tree, targetComponent, false) {

  override fun returnFocusToTree() {
    ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME)?.activate(null)
  }
}
