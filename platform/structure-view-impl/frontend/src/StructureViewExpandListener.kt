// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModel
import com.intellij.ui.ClientProperty
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath
import kotlin.Any

class StructureViewExpandListener internal constructor(private val tree: JTree, private val model: StructureUiModel) : TreeModelAdapter() {
  override fun treeNodesInserted(e: TreeModelEvent) {
    val parentPath = e.treePath
    if (true == ClientProperty.get(tree, StructureViewComponent.STRUCTURE_VIEW_STATE_RESTORED_KEY)) return
    if (parentPath == null || parentPath.getPathCount() > autoExpandDepth.asInteger() - 1) return
    val children = e.getChildren()
    if (model.smartExpand && children.size == 1) {
      expandLater(parentPath, children[0])
    }
    else {
      for (o in children) {
        val descriptor = TreeUtil.getUserObject(NodeDescriptor::class.java, o)
        if (descriptor != null && isAutoExpandNode(descriptor)) {
          expandLater(parentPath, o)
        }
      }
    }
  }

  fun expandLater(parentPath: TreePath, o: Any) {
    ApplicationManager.getApplication().invokeLater {
      if (!tree.isVisible(parentPath) || !tree.isExpanded(parentPath)) return@invokeLater
      try {
        if (tree is Tree) {
          tree.suspendExpandCollapseAccessibilityAnnouncements()
        }
        tree.expandPath(parentPath.pathByAddingChild(o))
      }
      finally {
        if (tree is Tree) {
          tree.resumeExpandCollapseAccessibilityAnnouncements()
        }
      }
    }
  }

  fun isAutoExpandNode(nodeDescriptor: NodeDescriptor<*>): Boolean {
    if (nodeDescriptor is StructureViewTreeElement)  return nodeDescriptor.value.shouldAutoExpand

    // expand root node & its immediate children
    val parent = nodeDescriptor.parentDescriptor
    return parent == null || parent.parentDescriptor == null
  }

  companion object {
    private val autoExpandDepth = Registry.get("ide.tree.autoExpandMaxDepth")
  }
}