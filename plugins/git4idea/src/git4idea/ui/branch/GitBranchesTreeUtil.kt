// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.ide.ui.UISettings
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

object GitBranchesTreeUtil {

  fun JTree.overrideBuiltInAction(actionKey: String, override: (ActionEvent) -> Boolean) {
    val originalAction = actionMap[actionKey]
    actionMap.put(actionKey, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (override(e)) return
        originalAction.actionPerformed(e)
      }
    })
  }

  private val cycleScrolling: Boolean
    get() = UISettings.getInstance().cycleScrolling

  fun JTree.selectNextLeaf(): Boolean {
    val nextLeaf = model.findNextLeaf(selectionPath, true)
    val toSelect = nextLeaf ?: if (cycleScrolling) model.findFirstLeaf() else null ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectPrevLeaf(): Boolean {
    val prevLeaf = model.findNextLeaf(selectionPath, false)
    val toSelect = prevLeaf ?: if (cycleScrolling) model.findLastLeaf() else null ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectFirstLeaf(): Boolean {
    val toSelect = model.findFirstLeaf() ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectLastLeaf(): Boolean {
    val toSelect = model.findLastLeaf() ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  private fun TreeModel.findNextLeaf(start: TreePath?, forward: Boolean): TreePath? {
    if (start == null) return null
    if (start.parentPath == null) return null

    return findChildLeaf(start, forward) ?: findNextSiblingLeaf(start, forward)
  }

  private fun TreeModel.findChildLeaf(parentPath: TreePath, forward: Boolean, startChild: TreePath? = null): TreePath? {
    val parent = parentPath.lastPathComponent
    val childCount = getChildCount(parent)

    if (childCount <= 0) {
      return null
    }

    val startIndex =
      if (startChild == null) {
        if (forward) 0 else childCount - 1
      }
      else {
        val startChildIndex = getIndexOfChild(parentPath.lastPathComponent, startChild.lastPathComponent)
        if (forward) startChildIndex + 1 else startChildIndex - 1
      }

    if (startIndex < 0 || startIndex >= childCount) {
      return null
    }

    val indices = if (forward) startIndex until childCount else startIndex downTo 0
    for (i in indices) {
      val child = getChild(parent, i)!!
      val childPath = parentPath.pathByAddingChild(child)

      if (isLeaf(child)) {
        return childPath
      }

      val childOfChild = findChildLeaf(childPath, forward)
      if (childOfChild != null) {
        return childOfChild
      }
    }
    return null
  }

  private fun TreeModel.findNextSiblingLeaf(start: TreePath, forward: Boolean): TreePath? {
    val parent = start.parentPath
    if (parent == null) return null
    return findChildLeaf(parent, forward, start) ?: findNextSiblingLeaf(start.parentPath, forward)
  }

  private fun TreeModel.findFirstLeaf(): TreePath? {
    val rootPath = TreePath(root)
    return findChildLeaf(rootPath, true)
  }

  private fun TreeModel.findLastLeaf(): TreePath? {
    val rootPath = TreePath(root)
    return findChildLeaf(rootPath, false)
  }
}