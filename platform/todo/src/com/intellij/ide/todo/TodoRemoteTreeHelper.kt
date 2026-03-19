// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.ide.todo.nodes.TodoRemoteItemNode
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

object TodoRemoteTreeHelper {

  @JvmStatic
  fun getNeighbourTreeNode(selectedNode: DefaultMutableTreeNode?, next: Boolean): DefaultMutableTreeNode? {
    if (!isRemoteTodoItemNode(selectedNode)) return null
    val selectedRemoteNode = selectedNode ?: return null
    return if (next) findNext(selectedRemoteNode) else findPrevious(selectedRemoteNode)
  }

  private fun findNext(selectedNode: DefaultMutableTreeNode): DefaultMutableTreeNode? {
    var candidateNode = selectedNode.nextNode
    while (candidateNode != null) {
      if (isRemoteTodoItemNode(candidateNode)) return candidateNode
      candidateNode = candidateNode.nextNode
    }
    return null
  }

  private fun findPrevious(selectedNode: DefaultMutableTreeNode): DefaultMutableTreeNode? {
    var candidateNode = selectedNode.previousNode
    while (candidateNode != null) {
      if (isRemoteTodoItemNode(candidateNode)) return candidateNode
      candidateNode = candidateNode.previousNode
    }
    return null
  }

  private fun isRemoteTodoItemNode(treeNode: TreeNode?): Boolean {
    return treeNode is DefaultMutableTreeNode && treeNode.userObject is TodoRemoteItemNode
  }
}