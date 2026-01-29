// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.pane

import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.pane.*
import com.intellij.pom.Navigatable
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import javax.swing.JComponent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

internal abstract class TreeBasedFrontendProjectViewPane : FrontendProjectViewPane, UiDataProvider {
  private val treeModel = DefaultTreeModel(null)
  private val tree = Tree(treeModel).also {
    it.isRootVisible = false
    CustomizationUtil.installPopupHandler(it, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP)
  }
  private val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private val contentPanel = ContentPanel(scrollPane)
  
  private inner class ContentPanel(content: JComponent) : SimpleToolWindowPanel(true), UiDataProvider {
    init {
      setContent(content)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      super.uiDataSnapshot(sink)
      this@TreeBasedFrontendProjectViewPane.uiDataSnapshot(sink)
    }
  }
  
  private val nodeById = hashMapOf<Long, Node>().also { 
    it[SUPER_ROOT_ID] = Node(SuperRootModel)
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
    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)
  }

  override fun applyStateChange(event: ProjectViewPaneStateEvent) {
    when (event) {
      is ProjectViewNodeAdded -> {
        val parent = getNodeById(event.parentId) ?: return
        val newNode = createNode(event.model)
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(newNode)
        }
        else {
          treeModel.insertNodeInto(newNode, parent, event.index)
        }
      }
      is ProjectViewNodeUpdated -> {
        val node = getNodeById(event.model.id) ?: return
        node.projectViewNode = event.model
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
  
  private fun createNode(model: ProjectViewNodeModel): Node {
    val result = Node(model)
    nodeById[model.id] = result
    return result
  }

  private fun removeNode(node: Node) {
    nodeById.remove(node.projectViewNode.id)
    for (node in node.children()) {
      removeNode(node as Node)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ProjectViewPaneId.DATA_KEY] = id
    sink[ProjectViewPaneProviderId.DATA_KEY] = providerId
    sink[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.id
    }
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.toNavigatable()
    }?.toTypedArray()
  }

  private fun ProjectViewNodeModel.toNavigatable(): Navigatable = NavigatableNode(this)

  private inner class NavigatableNode(private val model: ProjectViewNodeModel) : Navigatable {
    override fun navigate(requestFocus: Boolean) {
      check(_requestChannel.trySend(ProjectViewPaneNavigateRequest(model.id, requestFocus)).isSuccess)
    }

    override fun canNavigate(): Boolean = model.canNavigate()

    override fun canNavigateToSource(): Boolean = model.canNavigateToSource()
  }
}

private class Node(
  model: ProjectViewNodeModel,
) : DefaultMutableTreeNode(model), TreeNodeWithPresentation {
  var projectViewNode: ProjectViewNodeModel
    get() = userObject as ProjectViewNodeModel
    set(value) {
      userObject = value
    }

  override val presentation: TreeNodePresentation
    get() = projectViewNode.presentation

  override fun isLeaf(): Boolean {
    return projectViewNode.presentation.isLeaf
  }
}
