// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import javax.swing.tree.DefaultTreeModel

/**
 * Call [shutdown] when the browser is no longer needed.
 */
abstract class AsyncChangesBrowserBase(project: Project,
                                       showCheckboxes: Boolean,
                                       highlightProblems: Boolean)
  : ChangesBrowserBase(project, showCheckboxes, highlightProblems) {

  protected abstract val changesTreeModel: AsyncChangesTreeModel

  override fun createTreeList(project: Project,
                              showCheckboxes: Boolean,
                              highlightProblems: Boolean): AsyncChangesTree {
    return AsyncChangesBrowserTreeList(this, project, showCheckboxes, highlightProblems, false)
  }

  override fun getViewer(): AsyncChangesTree {
    return super.getViewer() as AsyncChangesTree
  }

  @Deprecated("Should not be called for AsyncChangesBrowserBase")
  final override fun buildTreeModel(): DefaultTreeModel {
    throw UnsupportedOperationException("Should not be called for AsyncChangesBrowserBase")
  }

  fun shutdown() {
    viewer.shutdown()
  }

  private class AsyncChangesBrowserTreeList(
    private val browser: AsyncChangesBrowserBase,
    project: Project,
    showCheckboxes: Boolean,
    highlightProblems: Boolean,
    showConflictsNode: Boolean
  ) : AsyncChangesTree(project, showCheckboxes, highlightProblems, showConflictsNode) {

    init {
      setDoubleClickAndEnterKeyHandler { browser.onDoubleClick() }
      setInclusionListener { browser.onIncludedChanged() }
      putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    }

    override val changesTreeModel: AsyncChangesTreeModel
      get() = browser.changesTreeModel
  }
}
