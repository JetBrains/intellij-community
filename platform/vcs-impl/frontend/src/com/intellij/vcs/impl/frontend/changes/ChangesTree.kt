// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Processor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserRootNode
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesTreeFrontendCellRenderer
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel

@Suppress("LeakingThis")
open class ChangesTree(val project: Project, val showCheckboxes: Boolean, val highlightProblems: Boolean) : Tree() {
  private var modelUpdateInProgress = false
  private val keyHandlers = ChangesTreeHandlers(this)

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

  fun setDoubleClickHandler(processor: Processor<in MouseEvent>) {
    keyHandlers.doubleClickHandler = processor
  }

  fun setEnterKeyHandler(processor: Processor<in KeyEvent>) {
    keyHandlers.enterKeyHandler = processor
  }

  fun getRoot(): ChangesBrowserRootNode {
    return model.root as ChangesBrowserRootNode
  }

  fun addSelectionListener(parent: Disposable? = null, listener: TreeSelectionListener) {
    addTreeSelectionListener(listener)
    if (parent != null) Disposer.register(parent) { removeTreeSelectionListener(listener) }
  }
}


private class ChangesTreeHandlers(private val tree: ChangesTree) {
  init {
    tree.addKeyListener(MyEnterListener())
    MyDoubleClickListener().installOn(tree)
  }

  var enterKeyHandler: Processor<in KeyEvent>? = null
  var doubleClickHandler: Processor<in MouseEvent>? = null

  private inner class MyEnterListener : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      val handler = enterKeyHandler ?: return

      if (VK_ENTER != e.keyCode || e.modifiers != 0) return
      if (tree.selectionCount <= 1 && !tree.isLeafSelected()) return

      if (handler.process(e)) e.consume()
    }
  }

  private inner class MyDoubleClickListener : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      val handler = doubleClickHandler ?: return false

      val clickPath = TreeUtil.getPathForLocation(tree, e.x, e.y)
      if (clickPath == null) return false

      //if (tree.getPathIfCheckBoxClicked(e.point) != null) return false

      return handler.process(e)
    }
  }
}

private fun ChangesTree.isLeafSelected(): Boolean = lastSelectedPathComponent?.let { model.isLeaf(it) } == true