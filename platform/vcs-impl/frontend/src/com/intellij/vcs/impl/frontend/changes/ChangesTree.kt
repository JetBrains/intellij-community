// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesTreeFrontendCellRenderer
import javax.swing.tree.DefaultTreeModel

open class ChangesTree(private val project: Project, val showCheckboxes: Boolean, val highlightProblems: Boolean) : Tree() {
  private var modelUpdateInProgress = false

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(project, { false }, false)
    setCellRenderer(ChangesTreeFrontendCellRenderer(nodeRenderer))
    setRootVisible(false)
  }

  open fun isInclusionVisible(node: ChangesBrowserNode<*>): Boolean {
    return true
  }

  protected fun updateTreeModel(
    model: DefaultTreeModel,
  ) {
    ThreadingAssertions.assertEventDispatchThread()
    modelUpdateInProgress = true
    try {
      setModel(model)
    }
    finally {
      modelUpdateInProgress = false
    }
  }
}