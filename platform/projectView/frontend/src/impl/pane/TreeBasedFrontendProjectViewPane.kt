// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.pane

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.pane.*
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import javax.swing.JComponent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

internal abstract class TreeBasedFrontendProjectViewPane : FrontendProjectViewPane {
  private val treeModel = DefaultTreeModel(null)
  private val tree = Tree(treeModel).also {
    it.isRootVisible = false
  }
  private val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private val contentPanel = SimpleToolWindowPanel(true).also { 
    it.setContent(scrollPane)
  }
  
  private val nodeById = hashMapOf<Long, Node>().also { 
    it[SUPER_ROOT_ID] = Node(SUPER_ROOT_ID, SuperRootPresentation as TreeNodePresentationImpl)
  }
  
  override val component: JComponent
    get() = contentPanel

  private val _requestChannel = Channel<ProjectViewPaneRequest>(Channel.UNLIMITED)

  override val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
    get() = _requestChannel

  init {
    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        val expandedNodeId = (event.path.lastPathComponent as? Node)?.projectViewNode?.id ?: return
        check(_requestChannel.trySend(ProjectViewPaneLoadChildrenRequest(expandedNodeId)).isSuccess)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) { }
    })
  }

  override fun applyStateChange(event: ProjectViewPaneStateEvent) {
    when (event) {
      is ProjectViewNodeAdded -> {
        val parent = getNodeById(event.parentId) ?: return
        val newNode = createNode(event.nodeId, event.presentation)
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(newNode)
        }
        else {
          treeModel.insertNodeInto(newNode, parent, event.index)
        }
      }
      is ProjectViewNodeUpdated -> {
        val node = getNodeById(event.nodeId) ?: return
        node.projectViewNode.presentation = event.presentation as TreeNodePresentationImpl
        treeModel.nodeChanged(node)
      }
      is ProjectViewChildRemoved -> {
        val parent = getNodeById(event.parentId) ?: return
        val child = getChild(parent, event.index) ?: return
        removeNode(child)
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(null)
        }
        else {
          treeModel.removeNodeFromParent(child)
        }
      }
      is ProjectViewChildrenRemoved -> {
        val parent = getNodeById(event.parentId) ?: return
        val childCount = parent.childCount
        val children = (0 until childCount).map { i -> parent.getChildAt(i) as Node }
        for (child in children) {
          removeNode(child)
        }
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(null)
        }
        else {
          parent.removeAllChildren()
          treeModel.nodesWereRemoved(parent, IntArray(childCount) { it }, children.toTypedArray())
        }
      }
    }
  }

  private fun getNodeById(id: Long): Node? {
    return nodeById[id]
  }

  private fun getChild(parent: Node, index: Int): Node? {
    return parent.getChildAt(index) as Node?
  }
  
  private fun createNode(id: Long, presentation: TreeNodePresentation): Node {
    val result = Node(id, presentation as TreeNodePresentationImpl)
    nodeById[id] = result
    return result
  }

  private fun removeNode(node: Node) {
    nodeById.remove(node.projectViewNode.id)
    for (node in node.children()) {
      removeNode(node as Node)
    }
  }
}

private class Node(
  id: Long,
  presentation: TreeNodePresentationImpl,
) : DefaultMutableTreeNode(ProjectViewNode(id, presentation)), TreeNodeWithPresentation {
  val projectViewNode: ProjectViewNode
    get() = userObject as ProjectViewNode

  override val presentation: TreeNodePresentation
    get() = projectViewNode.presentation

  override fun isLeaf(): Boolean {
    return projectViewNode.presentation.isLeaf
  }
}

private class ProjectViewNode(
  val id: Long,
  var presentation: TreeNodePresentationImpl,
)
