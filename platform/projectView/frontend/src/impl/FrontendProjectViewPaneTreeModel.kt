// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.actions.ProjectViewOptionMenuUpdater
import com.intellij.platform.projectView.actions.SplitProjectViewSelectInTarget
import com.intellij.platform.projectView.pane.ProjectViewChildRemoved
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewChildrenRemoved
import com.intellij.platform.projectView.pane.ProjectViewClearStateEvent
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.platform.projectView.pane.ProjectViewNodeMoved
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewNodeUpdated
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeFileNestingRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeOptionValueRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneChangeSortKeyRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionChanged
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.ProjectViewSelectNodeEvent
import com.intellij.platform.projectView.pane.ProjectViewSettingsStateEvent
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SuperRootModel
import com.intellij.platform.projectView.settings.NestingRuleDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateDTO
import com.intellij.pom.Navigatable
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * The model half of the tree-based frontend Project View pane.
 *
 * It owns the [treeModel] and everything needed to populate it from the [ProjectViewPaneStateEvent]s
 * produced by the backend pipeline: the [nodeById] index, node creation/removal, the outbound request
 * channel and the await-until-loaded helpers. It intentionally does not depend on any Swing component
 * (`JTree`/`JComponent`), so the whole pipeline (backend models -> events -> populated tree) can be
 * tested without instantiating the actual UI.
 */
internal class FrontendProjectViewPaneTreeModel(
  private val project: Project,
  internal val descriptor: ProjectViewPaneDescriptorImpl,
) {
  internal val treeModel = DefaultTreeModelWithCachedPresentation()

  private val optionSupport = ActionSupport()

  private val nodeById = hashMapOf<Long, Node>().also {
    it[SUPER_ROOT_ID] = Node(SuperRootModel)
  }

  private val updateEpoch = MutableStateFlow(0L)

  internal val selectInTargets: Collection<SelectInTarget> = descriptor.selectInTargetDescriptors.map {
    SplitProjectViewSelectInTarget(
      minorViewId = it.id,
      presentableName = it.presentableName,
      weight = it.weight
    )
  }

  internal val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
    field = Channel<ProjectViewPaneRequest>(Channel.UNLIMITED)

  /**
   * Node paths requested to be selected (via [ProjectViewSelectNodeEvent]). The UI consumes this channel
   * and performs the actual tree selection, because selection needs the `JTree` this class doesn't own.
   */
  internal val selectionRequests: ReceiveChannel<ProjectViewNodePath>
    field = Channel<ProjectViewNodePath>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  internal fun setCurrent(isCurrent: Boolean) {
    if (isCurrent) {
      sendRequest(ProjectViewPaneSelectionChanged(descriptor.id))
    }
  }

  private fun sendRequest(request: ProjectViewPaneRequest) {
    check(requestChannel.trySend(request).isSuccess)
  }

  /**
   * Requests the backend to load the children of the given node. Called by the UI when a node is expanded.
   */
  internal fun requestLoadChildren(nodeId: Long) {
    sendRequest(ProjectViewPaneLoadChildrenRequest(nodeId))
  }

  internal fun getOptionSupport(): ProjectViewActionSupport = optionSupport

  internal fun applyStateChange(event: ProjectViewPaneStateEvent) {
    when (event) {
      is ProjectViewClearStateEvent -> {
        treeModel.root = null
      }
      is ProjectViewChildrenLoaded -> {
        val parent = getNodeById(event.parentId) ?: return
        if (parent.id == SUPER_ROOT_ID) {
          updateRoot(event.children)
        }
        else {
          updateChildren(parent, event.children)
        }
        parent.isChildrenLoaded = true
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
      is ProjectViewNodeMoved -> {
        val parent = getNodeById(event.parentId) ?: return
        val node = getNodeById(event.childModel.id) ?: return
        treeModel.updateNode(node, event.childModel) // refresh the presentation
        // Reposition the same Node object (preserving its subtree and its nodeById entry) by
        // detaching and re-attaching it. removeChild + insertChild keep the Swing model consistent.
        val from = parent.getIndex(node)
        if (from >= 0 && from != event.newIndex) {
          treeModel.removeChild(parent, from)
          treeModel.insertChild(parent, event.newIndex, node)
        }
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
      is ProjectViewSettingsStateEvent -> {
        optionSupport.updateActionState(event.settingsState)
      }
      is ProjectViewSelectNodeEvent -> {
        // The actual selection needs the JTree, which lives in the UI. Enqueue the request instead;
        // the UI consumes selectionRequests and performs the selection. Doing it inline here would
        // block the event pipeline until the node loads (a latent deadlock).
        selectionRequests.trySend(event.nodePath)
      }
    }
    updateEpoch.update { it + 1 }
  }

  private fun updateRoot(newRootUserObjects: List<ProjectViewNodeModel>) {
    // The model may provide fake nodes with cached presentations,
    // so we need to take care to avoid cast exceptions.
    val existingRoot = treeModel.root as? Node?
    if (newRootUserObjects.isEmpty()) {
      LOG.debug { "The root is removed" }
      if (existingRoot != null) {
        removeNode(existingRoot)
      }
      treeModel.setRoot(null)
    }
    else if (newRootUserObjects.size > 1) {
      LOG.error("Got ${newRootUserObjects.size} roots: $newRootUserObjects")
    }
    else {
      val newRootUserObject = newRootUserObjects.single()
      if (existingRoot?.projectViewNode?.id != newRootUserObject.id) {
        if (existingRoot != null) {
          removeNode(existingRoot)
        }
        treeModel.setRoot(createNode(newRootUserObject))
      }
      else {
        treeModel.updateNode(existingRoot, newRootUserObject)
      }
    }
  }

  private fun updateChildren(
    parent: Node,
    newChildUserObjects: List<ProjectViewNodeModel>,
  ) {
    // It's a bit tricky because updating children in the model will keep
    // the existing nodes for already-present children.
    // But we must maintain the nodeById map consistent.
    // The easiest way is to remove everything and then add everything.
    // But there's another tricky part: the removed-for-good nodes should be removed recursively.
    // So we keep track of them and then remove once we're done.
    val existingChildrenById = Long2ObjectOpenHashMap<Node>()
    val removedChildrenById = Long2ObjectOpenHashMap<Node>()
    // The existing children may be fake cached nodes,
    // so we need to take care to avoid cast exceptions.
    parent.children().asSequence().filterIsInstance<Node>().forEach { existingChild ->
      existingChildrenById[existingChild.id] = existingChild
      nodeById.remove(existingChild.id)
      removedChildrenById[existingChild.id] = existingChild
    }
    // Now there are no child nodes in the nodeById map.
    val newChildren = ArrayList<Node>()
    newChildUserObjects.forEach { newChildUserObject ->
      removedChildrenById.remove(newChildUserObject.id)
      newChildren.add(Node(newChildUserObject))
    }
    // Now removedChildrenById contain only the children that are going to be permanently removed.

    treeModel.updateChildren(parent, newChildren) { newChild ->
      existingChildrenById[(newChild as Node).id] // safe cast: no cached nodes among the new ones
    }
    // Now the parent has the new children: some are reused nodes with updated user objects, some are now.

    // Add back the new nodes.
    parent.children().asSequence().forEach { newChild ->
      nodeById[(newChild as Node).id] = newChild // safe cast: all cached children are gone
    }

    // Remove the permanently removed nodes now.
    removedChildrenById.values.forEach { removedChild ->
      removeNode(removedChild) // the node itself is already removed, but this will remove its descendants
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
    nodeById.remove(node.id)
    for (child in node.children()) {
      val childNode = child as? Node ?: continue // skip cached children
      removeNode(childNode)
    }
  }

  internal suspend fun awaitNodePath(nodeId: Long): TreePath {
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

  internal suspend fun awaitNodeChildren(node: Node, condition: () -> Boolean): List<Node>? {
    if (!condition()) return null
    if (!node.isChildrenLoaded) { // request in the case it wasn't requested before
      sendRequest(ProjectViewPaneLoadChildrenRequest(node.id))
    }
    var epoch = 0L
    while (condition()) {
      if (node.isChildrenLoaded) {
        // The cast to Node should be safe now, as isChildrenLoaded implies no cached children.
        return node.children().asSequence().map { it as Node }.toList()
      }
      epoch = updateEpoch.first { it > epoch }
    }
    return null
  }

  internal fun createNavigatable(model: ProjectViewNodeModel): Navigatable = NavigatableNode(model)

  private inner class NavigatableNode(private val model: ProjectViewNodeModel) : Navigatable {
    override fun navigate(requestFocus: Boolean) {
      sendRequest(ProjectViewPaneNavigateRequest(model.id, requestFocus))
    }

    override fun canNavigate(): Boolean = model.canNavigate()

    override fun canNavigateToSource(): Boolean = model.canNavigateToSource()
  }

  private inner class ActionSupport : ProjectViewActionSupport {
    private val actionState = MutableStateFlow<ProjectViewPaneSettingsStateDTO?>(null)

    override fun getActionState(): ProjectViewPaneSettingsStateDTO? = actionState.value

    override fun getActionStateFlow(): Flow<ProjectViewPaneSettingsStateDTO?> = actionState.asStateFlow()

    override fun requestOptionValueChange(option: ProjectViewPaneOptionDTO, newValue: Boolean) {
      sendRequest(ProjectViewPaneChangeOptionValueRequest(option, newValue))
    }

    override fun requestSortKeyChange(sortKey: NodeSortKey) {
      sendRequest(ProjectViewPaneChangeSortKeyRequest(sortKey))
    }

    override fun requestFileNestingChange(
      fileNestingOn: Boolean,
      activeRules: List<NestingRuleDTO>,
    ) {
      sendRequest(ProjectViewPaneChangeFileNestingRequest(fileNestingOn, activeRules))
    }

    fun updateActionState(actionState: ProjectViewPaneSettingsStateDTO) {
      LOG.debug { "Received updated actions: $actionState" }
      this.actionState.value = actionState
      ProjectViewOptionMenuUpdater.getInstance(project).updateMenu()
    }
  }
}

internal class Node(
  model: ProjectViewNodeModel,
) : DefaultMutableTreeNode(model), TreeNodeWithPresentation, PathElementIdProvider {
  val projectViewNode: ProjectViewNodeModelImpl<*>
    get() = userObject as ProjectViewNodeModelImpl<*>

  var isChildrenLoaded: Boolean = false

  val id: Long
    get() = projectViewNode.id

  override val presentation: TreeNodePresentationImpl
    get() = projectViewNode.presentation

  override fun isLeaf(): Boolean {
    return projectViewNode.presentation.isLeaf
  }

  override fun getPathElementId(): String = presentation.mainText

  override fun toString(): String = "{[${projectViewNode.id}] ${projectViewNode.presentation.mainText}}"
}

private val LOG = logger<FrontendProjectViewPaneTreeModel>()
