// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jdom.Element
import javax.swing.JComponent

/**
 * A temporary frontend-only pane that shows the cached tree of a saved pane.
 *
 * The tool window shows it while the pane descriptors load.
 * It has no real nodes and sends no requests.
 * The first real pane replaces it.
 * The tool window saves the state of the cached pane when it removes it, so the real pane with the same [id] restores this state.
 */
@Suppress("ConvertToExplicitBackingFields") // the tree is used in more than one place
internal class CachedFrontendProjectViewPane(
  val id: ProjectViewPaneId,
  val presentableName: @NlsSafe String,
) {
  private val tree = FrontendProjectViewTree(DefaultTreeModelWithCachedPresentation())

  val component: JComponent = SimpleToolWindowPanel(true).apply {
    setContent(ScrollPaneFactory.createScrollPane(tree, true))
  }

  val componentToFocus: JComponent
    get() = tree

  /**
   * Shows the cached presentation from the saved pane state.
   *
   * @return `true` if the state has a cached presentation
   */
  @RequiresEdt
  fun restoreStateFrom(element: Element): Boolean {
    val treeState = TreeState.createFrom(element)
    if (treeState.cachedPresentationData == null) return false
    treeState.applyTo(tree, false)
    if (tree.selectionPath == null && tree.rowCount > 0) {
      tree.selectionPath = tree.getPathForRow(0)
    }
    return true
  }

  /**
   * Saves the expanded nodes, the selection and the cached presentation of the shown tree.
   */
  @RequiresEdt
  fun saveStateTo(element: Element) {
    TreeState.createOn(tree, true, true, true).writeExternal(element)
  }
}
