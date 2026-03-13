// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.impl.pane

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.actions.NestingRuleState
import com.intellij.platform.projectView.actions.ProjectViewActionState
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.actions.ProjectViewOptionMenuUpdater
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewActionStateEvent
import com.intellij.platform.projectView.pane.ProjectViewChildRemoved
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewChildrenRemoved
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewNodeUpdated
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeFileNestingRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeOptionValueRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeSortKeyRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionChanged
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SuperRootModel
import com.intellij.pom.Navigatable
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal abstract class TreeBasedFrontendProjectViewPane(
  private val project: Project,
) : FrontendProjectViewPane, UiDataProvider {
  private val treeModel = DefaultTreeModelWithCachedPresentation()
  private val tree = Tree(treeModel).also {
    it.isRootVisible = false
    CustomizationUtil.installPopupHandler(it, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP)
  }
  private val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private val contentPanel = ContentPanel(scrollPane)
  private val treeExpander = ProjectViewTreeExpander(tree)

  private val optionSupport = ActionSupport()
  
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

  private val updateEpoch = MutableStateFlow(0L)
  
  override val component: JComponent
    get() = contentPanel

  private val _requestChannel = Channel<ProjectViewPaneRequest>(Channel.UNLIMITED)

  override val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
    get() = _requestChannel

  override var isCurrent: Boolean = false
    set(value) {
      field = value
      if (isCurrent) {
        sendRequest(ProjectViewPaneSelectionChanged(id))
      }
    }

  init {
    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        val expandedNodeId = (event.path.lastPathComponent as? Node)?.projectViewNode?.id ?: return
        val request = ProjectViewPaneLoadChildrenRequest(expandedNodeId)
        sendRequest(request)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) { }
    })
    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)
  }

  private fun sendRequest(request: ProjectViewPaneRequest) {
    check(_requestChannel.trySend(request).isSuccess)
  }

  override suspend fun manage() {
    awaitCancellation()
  }

  override fun getOptionSupport(): ProjectViewActionSupport = optionSupport

  override fun applyStateChange(event: ProjectViewPaneStateEvent) {
    when (event) {
      is ProjectViewChildrenLoaded -> {
        if (event.parentId == SUPER_ROOT_ID) {
          val newNode = createNode(event.children.single())
          treeModel.setRoot(newNode)
        }
        else {
          val parent = getNodeById(event.parentId) ?: return
          val children = event.children.map { createNode(it) }
          treeModel.setChildren(parent, children)
        }
      }
      is ProjectViewNodeAdded -> {
        val parent = getNodeById(event.parentId) ?: return
        val newNode = createNode(event.model)
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(newNode)
        }
        else {
          treeModel.insertChild(parent, event.index, newNode)
        }
      }
      is ProjectViewNodeUpdated -> {
        val node = getNodeById(event.model.id) ?: return
        treeModel.updateNode(node, event.model)
      }
      is ProjectViewChildRemoved -> {
        val parent = getNodeById(event.parentId) ?: return
        val child = getChild(parent, event.index) ?: return
        removeNode(child)
        if (event.parentId == SUPER_ROOT_ID) {
          treeModel.setRoot(null)
        }
        else {
          treeModel.removeChild(parent, event.index)
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
          treeModel.setChildren(parent, emptyList())
        }
      }
      is ProjectViewActionStateEvent -> {
        optionSupport.updateActionState(event.actionState)
      }
    }
    updateEpoch.update { it + 1 }
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

  suspend fun selectNode(nodePath: ProjectViewNodePath) {
    withContext(Dispatchers.UI) {
      val treePath = awaitNodePath(nodePath.nodeIds.last())
      TreeUtil.selectPath(tree, treePath)
    }
  }

  private suspend fun awaitNodePath(nodeId: Long): TreePath {
    var epoch = 0L
    while (true) {
      val node = nodeById[nodeId]
      if (node == null) {
        epoch = updateEpoch.first { it > epoch }
      }
      else {
        return CachingTreePath(node.path)
      }
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ProjectViewPaneId.DATA_KEY] = id
    sink[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.id
    }
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.toNavigatable()
    }?.toTypedArray()
    sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
  }

  private fun ProjectViewNodeModel.toNavigatable(): Navigatable = NavigatableNode(this)

  private inner class NavigatableNode(private val model: ProjectViewNodeModel) : Navigatable {
    override fun navigate(requestFocus: Boolean) {
      sendRequest(ProjectViewPaneNavigateRequest(model.id, requestFocus))
    }

    override fun canNavigate(): Boolean = model.canNavigate()

    override fun canNavigateToSource(): Boolean = model.canNavigateToSource()
  }

  override fun saveStateTo(element: Element) {
    TreeState.createOn(tree, true, false, true).writeExternal(element)
  }

  override fun restoreStateFrom(element: Element) {
    TreeState.createFrom(element).applyTo(tree)
  }
  
  private inner class ActionSupport : ProjectViewActionSupport {
    private val actionState = AtomicReference<ProjectViewActionState?>(null)

    override fun getActionState(): ProjectViewActionState? = actionState.load()

    override fun requestOptionValueChange(option: ProjectViewOption, newValue: Boolean) {
      sendRequest(ProjectViewPaneChangeOptionValueRequest(option, newValue))
    }

    override fun requestSortKeyChange(sortKey: NodeSortKey) {
      sendRequest(ProjectViewPaneChangeSortKeyRequest(sortKey))
    }

    override fun requestFileNestingChange(
      fileNestingOn: Boolean,
      activeRules: List<NestingRuleState>,
    ) {
      sendRequest(ProjectViewPaneChangeFileNestingRequest(fileNestingOn, activeRules))
    }

    fun updateActionState(actionState: ProjectViewActionState) {
      LOG.debug { "Received updated actions: $actionState" }
      this.actionState.store(actionState)
      ProjectViewOptionMenuUpdater.getInstance(project).updateMenu()
    }
  }
}

private class Node(
  model: ProjectViewNodeModel,
) : DefaultMutableTreeNode(model), TreeNodeWithPresentation, PathElementIdProvider {
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

  override fun getPathElementId(): String = (presentation as TreeNodePresentationImpl).mainText
}

private class ProjectViewTreeExpander(tree: Tree) : DefaultTreeExpander(tree) {
  override fun isExpandAllVisible(): Boolean {
    return Registry.`is`("ide.project.view.expand.all.action.visible") && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun isExpandAllEnabled(): Boolean {
    return super.isExpandAllEnabled() && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun collapseAll(tree: JTree, strict: Boolean, keepSelectionLevel: Int) {
    super.collapseAll(tree, false, keepSelectionLevel)
  }
}

private val LOG = logger<TreeBasedFrontendProjectViewPane>()
