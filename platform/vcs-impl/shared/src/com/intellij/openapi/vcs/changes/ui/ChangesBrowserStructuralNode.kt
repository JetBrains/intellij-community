// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.util.LineRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.ApiStatus

/**
 * A structural element below a changed file. File actions use its enclosing change; navigation uses the element.
 *
 * A node with [hasInclusion] shows its own commit checkbox. The tree asks [getInclusionState] for the state and calls [setIncluded] on a
 * toggle. Other nodes reserve the checkbox space only.
 */
@ApiStatus.Internal
abstract class ChangesBrowserStructuralNode<T>(userObject: T, val hasInclusion: Boolean = false) : ChangesBrowserNode<T>(userObject) {
  init {
    markAsHelperNode()
    if (!hasInclusion) putUserData(ChangesBrowserNodeKeys.HIDE_INCLUSION_CHECKBOX, true)
  }

  abstract fun getNavigatable(): Navigatable?

  @RequiresReadLock
  @RequiresBackgroundThread
  open fun getPreviewNavigationRequest(): NavigationRequest? =
    getNavigatable()?.takeIf { it.canNavigateToSource() }?.navigationRequest()

  @RequiresReadLock
  @RequiresBackgroundThread
  open fun getPreviewHighlightRange(): TextRange? = null

  open fun getPreviewFile(): VirtualFile? = null

  /** Returns the lines of the diff hunk that this node shows, or null when the node is not a hunk. */
  open fun getDiffLines(): DiffLines? = null

  /** Returns the commit inclusion of this node. The state of the enclosing file applies when the node has no state of its own. */
  @RequiresEdt
  open fun getInclusionState(fileState: ThreeStateCheckBox.State): ThreeStateCheckBox.State = fileState

  /** Includes this node in the commit or excludes it. */
  @RequiresEdt
  open fun setIncluded(included: Boolean) {
  }

  /** Reverts the changes of this node and of the other selected nodes with the same rollback. Null when the node has no change to revert. */
  open fun getRollback(): Rollback? = null

  final override fun getFileCount(): Int = 0

  final override fun getDirectoryCount(): Int = 0

  fun getChangeNode(): ChangesBrowserNode<*>? {
    var node: ChangesBrowserNode<*>? = getParent()
    while (node != null) {
      if (node.userObject is Change) return node
      node = node.getParent()
    }
    return null
  }

  /** The lines of a diff hunk: [base] in the base revision and [current] in the current document. */
  data class DiffLines(val base: LineRange, val current: LineRange)

  /** Reverts several nodes in one step, so that the first change does not move the lines of the other changes. */
  fun interface Rollback {
    @RequiresEdt
    fun rollback(nodes: List<ChangesBrowserStructuralNode<*>>)
  }
}
