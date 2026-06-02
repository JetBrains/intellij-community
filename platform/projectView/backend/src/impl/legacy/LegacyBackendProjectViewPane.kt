// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.legacy

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.projectView.actions.ProjectViewActionState
import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.actions.ProjectViewOptionState
import com.intellij.platform.projectView.actions.ProjectViewSortKeyState
import com.intellij.platform.projectView.actions.legacyProjectViewOption
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPane
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPaneProvider
import com.intellij.platform.projectView.backend.pane.projectViewPaneStateBuilder
import com.intellij.platform.projectView.impl.legacy.LEGACY_PROVIDER_ID
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewActionStateEvent
import com.intellij.platform.projectView.pane.ProjectViewChildRemoved
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewChildrenRemoved
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodeUpdated
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.ProjectViewPaneUpdateOptionValueRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneUpdateSortKeyRequest
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SuperRoot
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.ui.ComponentUtil
import com.intellij.ui.LoadingNode
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.TreeNodePresentationBuilderImpl
import com.intellij.ui.tree.buildPresentation
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

internal class LegacyBackendProjectViewPaneProvider : BackendProjectViewPaneProvider {
  override val id: ProjectViewPaneProviderId
    get() = LEGACY_PROVIDER_ID

  override fun createPanes(project: Project): List<BackendProjectViewPane> {
    return project.service<LegacyBackendProjectViewPaneService>().createPanes()
  }
}

@Service(Service.Level.PROJECT)
private class LegacyBackendProjectViewPaneService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun createPanes(): List<BackendProjectViewPane> {
    return AbstractProjectViewPane.EP.getExtensions(project).flatMap { legacyPane ->
      createLegacyPanes(legacyPane)
    }
  }

  private fun createLegacyPanes(legacyPane: AbstractProjectViewPane): Iterable<BackendProjectViewPane> {
    val stateManager = AbstractProjectViewPaneStateManager(project, coroutineScope.childScope("LegacyBackendProjectViewPane: $legacyPane"), legacyPane)
    val subIds = legacyPane.subIds
    if (subIds.isEmpty()) {
      return listOf(LegacyBackendProjectViewPane(stateManager, null))
    }
    else {
      return subIds.map { subId -> LegacyBackendProjectViewPane(stateManager, subId) }
    }
  }
}

private class LegacyBackendProjectViewPane(
  private val legacyPaneManager: AbstractProjectViewPaneStateManager,
  private val subId: String?,
) : BackendProjectViewPane {
  override val descriptor: ProjectViewPaneDescriptor = ProjectViewPaneDescriptor(
    id = projectViewPaneId(if (subId == null) legacyPaneManager.id else "${legacyPaneManager.id}:$subId"),
    presentableName = if (subId == null) legacyPaneManager.legacyPane.title else legacyPaneManager.legacyPane.getPresentableSubIdName(subId),
    order = legacyPaneManager.legacyPane.weight,
  )

  private val requestChannel = Channel<ProjectViewPaneRequest>(capacity = Channel.UNLIMITED)

  override suspend fun manage() {
    coroutineScope {
      launch(CoroutineName("Manage pane ${descriptor.id}")) {
        legacyPaneManager.managePane()
      }
      launch(CoroutineName("Manage request channel ${descriptor.id}")) {
        val targetChannel = legacyPaneManager.getRequestChannel()
        for (request in requestChannel) {
          targetChannel.send(request)
        }
      }
    }
  }

  override fun getRequestChannel(): SendChannel<ProjectViewPaneRequest> {
    return requestChannel
  }

  override suspend fun getPaneStateFlow(): Flow<ProjectViewPaneStateEvent> {
    legacyPaneManager.subId = subId
    return legacyPaneManager.getStateFlow()
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val selectedIds = snapshot[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] ?: return
    legacyPaneManager.uiDataSnapshot(sink, selectedIds)
  }
}

private class AbstractProjectViewPaneStateManager(
  private val project: Project,
  coroutineScope: CoroutineScope,
  val legacyPane: AbstractProjectViewPane,
) {
  val id: String
    get() = legacyPane.id

  var subId: String?
    get() = subIdFlow.value
    set(value) {
      subIdFlow.value = value
    }
  
  private val paneCount = MutableStateFlow(0)

  private val subIdFlow = MutableStateFlow<String?>(null)
  
  private val requestChannel = Channel<ProjectViewPaneRequest>(capacity = Channel.BUFFERED)

  private val modelUpdateChannel = Channel<ModelUpdateRequest>(capacity = Channel.UNLIMITED)

  private val stateBuilder = projectViewPaneStateBuilder()
  
  private var nextNodeId = SUPER_ROOT_ID
  
  private val nodeById = hashMapOf<Long, LegacyProjectViewNode>()

  private val nodeByModelNode = hashMapOf<Any, LegacyProjectViewNode>()
  
  private lateinit var treeModel: AsyncTreeModel
  
  init {
    coroutineScope.launch(CoroutineName("AbstractProjectViewPaneStateManager")) { 
      paneCount.first { it > 0 }
      val manageStateJob = launch(CoroutineName("manageState")) {
        manageState()
      }
      paneCount.first { it == 0 }
      manageStateJob.cancel()
    }
  }

  fun getRequestChannel(): SendChannel<ProjectViewPaneRequest> = requestChannel
  
  fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = stateBuilder.getStateFlow()

  suspend fun managePane() {
    paneCount.update { it + 1 }
    try {
      awaitCancellation()
    }
    finally {
      paneCount.update { it - 1 }  
    }
  }

  private suspend fun manageState() {
    coroutineScope {
      withContext(Dispatchers.UI) {
        var treeModelListener: MyTreeModelListener? = null
        try {
          launch(CoroutineName("subId updates")) {
            subIdFlow.collect { subId ->
              legacyPane.subId = subId
            }
          }
          val component = legacyPane.createComponent()
          treeModel = findTreeModel(component) ?: return@withContext
          treeModelListener = MyTreeModelListener()
          treeModel.addTreeModelListener(treeModelListener)
          loadInitialState()
          launch(CoroutineName("tree model events")) {
            LOG.debug { "Updating state for pane $id" }
            for (request in modelUpdateChannel) {
              val event = buildStateUpdateEvent(request)
              LOG.trace { "Applying state update for pane $id: $event" }
              stateBuilder.updateState(event)
            }
          }
          launch(CoroutineName("requests from the frontend")) {
            LOG.debug { "Processing requests from the frontend for pane $id" }
            for (request in requestChannel) {
              LOG.trace { "Got request for pane $id: $request" }
              when (request) {
                is ProjectViewPaneLoadChildrenRequest -> loadChildren(request.nodeId)
                is ProjectViewPaneNavigateRequest -> navigate(request.nodeId)
                is ProjectViewPaneUpdateOptionValueRequest -> updateOptionValue(request.option, request.newValue)
                is ProjectViewPaneUpdateSortKeyRequest -> updateSortKey(request.sortKey)
              }
            }
          }
          awaitCancellation()
        }
        finally {
          if (treeModelListener != null) {
            treeModel.removeTreeModelListener(treeModelListener)
          }
          Disposer.dispose(legacyPane)
        }
      }
    }
  }

  private suspend fun buildStateUpdateEvent(request: ModelUpdateRequest): ProjectViewPaneStateEvent {
    return when (request) {
      is ModelChildrenLoaded -> {
        ProjectViewChildrenLoaded(request.parentId, request.children.map { createNodeModel(it.id, it.modelNode) })
      }
      is ModelNodeAdded -> {
        ProjectViewNodeAdded(request.parentId, request.index, createNodeModel(request.nodeId, request.modelNode))
      }
      is ModelNodeUpdated -> {
        ProjectViewNodeUpdated(createNodeModel(request.nodeId, request.modelNode))
      }
      is ModelChildrenRemoved -> {
        ProjectViewChildrenRemoved(request.parentId)
      }
      is ModelChildRemoved -> {
        ProjectViewChildRemoved(request.parentId, request.index)
      }
      is ModelActionStatesUpdated -> {
        ProjectViewActionStateEvent(request.actionState)
      }
    }
  }

  private suspend fun createNodeModel(id: Long, node: Any): ProjectViewNodeModel {
    val presentation = getNodePresentation(node)
    val canNavigate = readAction { canNavigate(node) }
    val canNavigateToSource = readAction { canNavigateToSource(node) }
    return ProjectViewNodeModel(id, presentation, canNavigate, canNavigateToSource)
  }

  private fun getNodePresentation(node: Any): TreeNodePresentationImpl {
    val builder = TreeNodePresentationBuilderImpl(treeModel.isLeaf(node))
    return when (val userObject = TreeUtil.getUserObject(node)) {
      is PresentableNodeDescriptor<*> -> {
        buildPresentation(userObject, builder)
      }
      else -> {
        builder.apply {
          setMainText(userObject.toString())
        }.build()
      }
    }
  }

  @RequiresReadLock
  private fun canNavigate(node: Any): Boolean = (TreeUtil.getUserObject(node) as? Navigatable?)?.canNavigate() == true

  @RequiresReadLock
  private fun canNavigateToSource(node: Any): Boolean = (TreeUtil.getUserObject(node) as? Navigatable?)?.canNavigateToSource() == true

  private suspend fun navigate(id: Long) {
    val node = nodeById[id] ?: return
    val navigatable = TreeUtil.getUserObject(node.modelNode) as? Navigatable? ?: return
    val navigationRequest = readAction { navigatable.navigationRequest() } ?: return
    NavigationService.getInstance(project).navigate(navigationRequest)
  }

  private fun loadInitialState() {
    updateActionStates()
    LOG.trace("Adding the super root")
    addNode(null, 0, SuperRoot)
    LOG.trace("Loading the real root")
    loadChildren(SUPER_ROOT_ID)
  }

  private fun findTreeModel(component: JComponent): AsyncTreeModel? {
    val treeModel = ComponentUtil.findComponentsOfType(component, JTree::class.java).firstOrNull()?.model as? AsyncTreeModel?
    if (treeModel == null) {
      LOG.warn("Could not find the model")
    }
    return treeModel
  }

  private fun loadChildren(parentId: Long) {
    LOG.trace { "Processing the request to load the children of the node $parentId..." }
    val parent = nodeById[parentId] ?: return
    LOG.trace { "...which is $parent" }
    if (parent.childrenState != ChildrenState.NOT_LOADED) {
      LOG.trace("...but it has its children already ${parent.childrenState}, done")
      return
    }
    parent.childrenState = ChildrenState.LOADING
    tryLoadChildren(parent)
  }

  private fun tryLoadChildren(parent: LegacyProjectViewNode) {
    val modelNodes = getModelChildren(parent) ?: return
    finishLoadingChildren(parent, modelNodes)
  }
  
  private fun getModelChildren(parent: LegacyProjectViewNode): List<Any>? {
    return if (parent.id == SUPER_ROOT_ID) {
      val modelRoot = treeModel.root
      if (modelRoot == null) {
        LOG.trace("The backing model has no root yet, will wait until it's loaded")
        return null
      }
      if (modelRoot is LoadingNode) {
        LOG.trace("The backing model is still loading the root, will wait until it's loaded")
        return null
      }
      listOf(modelRoot)
    }
    else {
      val childCount = treeModel.getChildCount(parent.modelNode)
      if (childCount == 1 && treeModel.getChild(parent.modelNode, 0) is LoadingNode) {
        LOG.trace("The backing model is still loading the children, will wait until they're loaded")
        return null
      }
      (0 until childCount).map { treeModel.getChild(parent.modelNode, it) }
    }
  }

  private fun finishLoadingChildren(
    parent: LegacyProjectViewNode,
    modelNodes: List<Any>,
  ) {
    setChildren(parent.id, modelNodes)
    parent.childrenState = ChildrenState.LOADED
    LOG.trace { "Loaded ${modelNodes.size} children of ${parent.id}" }
    if (parent.id == SUPER_ROOT_ID) {
      val newRootId = nodeByModelNode.getValue(modelNodes.single()).id
      LOG.trace("We have a new real root (id=$newRootId), loading its children immediatly")
      loadChildren(newRootId)
    }
  }

  private fun handleModelUpdate(update: ModelUpdateRequest) {
    val result = modelUpdateChannel.trySend(update)
    check(result.isSuccess || result.isClosed)
  }

  private fun updateNodeValue(modelNode: Any) {
    LOG.trace { "Updating the presentation of the node $modelNode..." }
    val id = getNodeByModelNode(modelNode)?.id ?: return
    LOG.trace { "...which has the ID $id" }
    handleModelUpdate(ModelNodeUpdated(id, modelNode))
  }
  
  private fun updateNodeStructure(modelParent: Any) {
    updateNodeValue(modelParent)
    val parent = getNodeByModelNode(modelParent) ?: return
    when (parent.childrenState) {
      ChildrenState.NOT_LOADED -> { }
      ChildrenState.LOADING -> {
        tryLoadChildren(parent)
      }
      ChildrenState.LOADED -> {
        updateExistingChildren(parent)
      }
    }
  }

  private fun updateExistingChildren(parent: LegacyProjectViewNode) {
    handleModelUpdate(ModelChildrenRemoved(parent.id))
    val modelChildren = getModelChildren(parent) ?: emptyList()
    for ((index, child) in modelChildren.withIndex()) {
      addNode(parent.id, index, child)
    }
  }

  private fun insertChildren(modelParent: Any, newIndices: IntArray) {
    val parent = getNodeByModelNode(modelParent) ?: return
    when (parent.childrenState) {
      ChildrenState.NOT_LOADED -> { }
      ChildrenState.LOADING -> {
        tryLoadChildren(parent)
      }
      ChildrenState.LOADED -> {
        for (i in newIndices) {
          val modelChild = treeModel.getChild(modelParent, i)
          addNode(parent.id, i, modelChild)
        }
      }
    }
  }
  
  private fun setChildren(parentId: Long, modelNodes: List<Any>) {
    val newNodes = modelNodes.map { createNewNode(parentId, it) }
    handleModelUpdate(ModelChildrenLoaded(parentId, newNodes.map { ModelChildDescriptor(it.id, it.modelNode) }))
  }

  private fun addNode(parentId: Long?, index: Int, modelNode: Any) {
    val newNode = createNewNode(parentId, modelNode)
    if (parentId != null) {
      handleModelUpdate(ModelNodeAdded(parentId, index, newNode.id, modelNode))
    }
  }

  private fun createNewNode(
    parentId: Long?,
    modelNode: Any,
  ): LegacyProjectViewNode {
    val newNodeId = nextNodeId++
    val newNode = LegacyProjectViewNode(newNodeId, parentId, modelNode)
    LOG.trace { "Adding $newNode" }
    nodeByModelNode[modelNode] = newNode
    nodeById[newNodeId] = newNode
    return newNode
  }

  private fun removeChild(modelParent: Any, index: Int) {
    val parent = getNodeByModelNode(modelParent) ?: return
    // In the LOADING state, a "removed" even means the "loading" node was removed, but we don't care about it.
    if (parent.childrenState == ChildrenState.LOADED) {
      handleModelUpdate(ModelChildRemoved(parent.id, index))
    }
  }

  private fun getNodeByModelNode(node: Any): LegacyProjectViewNode? {
    return nodeByModelNode[node]
  }

  private fun updateOptionValue(option: ProjectViewOption, newValue: Boolean) {
    val legacyOption = legacyProjectViewOption(project, option)
    legacyOption.isSelected = newValue
    updateActionStates()
  }

  private fun updateSortKey(sortKey: NodeSortKey) {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    impl.setSortKey(id, sortKey)
    updateActionStates()
  }

  private fun updateActionStates() {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    impl.changeView(id)
    val updatedOptionStates = ProjectViewOption.entries.associateWith { option ->
      val legacyOption = legacyProjectViewOption(project, option)
      ProjectViewOptionState(
        isSelected = legacyOption.isSelected,
        isEnabled = legacyOption.isEnabled,
        isAlwaysVisible = legacyOption.isAlwaysVisible,
      )
    }
    val updatedSortKeyState = ProjectViewSortKeyState(
      sortKey = impl.getSortKey(id),
      availableSortKeys = NodeSortKey.entries.filter { impl.isSortKeySupported(id, it) }.toSet()
    )
    LOG.debug { "Updated option states: $updatedOptionStates" }
    handleModelUpdate(ModelActionStatesUpdated(
      ProjectViewActionState(updatedOptionStates, updatedSortKeyState)
    ))
  }

  fun uiDataSnapshot(sink: DataSink, selectedIds: List<Long>) {
    val tree = legacyPane.tree ?: return
    val selectedPaths = selectedIds.mapNotNull { id ->
      nodeById[id]?.treePath()
    }
    tree.selectionPaths = selectedPaths.toTypedArray()
    legacyPane.uiDataSnapshot(sink)
    sink[LangDataKeys.IDE_VIEW] = IdeViewForProjectViewPane { legacyPane }
  }
  
  private fun LegacyProjectViewNode.treePath(): TreePath? {
    if (parentId == null) return CachingTreePath(modelNode)
    val parent = nodeById[parentId]
    if (parent == null) { // should not be possible
      LOG.error("No parent found for node $this")
      return null // can't really recover, skip this node and hope other are OK
    }
    return parent.treePath()?.pathByAddingChild(modelNode)
  }

  private inner class MyTreeModelListener : TreeModelListener {
    override fun treeNodesChanged(e: TreeModelEvent) {
      val treePath = e.treePath
      if (treePath == null) {
        updateNodeValue(SuperRoot)
        return
      }
      val childIndices = e.childIndices
      val parent = treePath.lastPathComponent
      if (childIndices == null) {
        updateNodeValue(parent)
        return
      }
      val model = e.model
      for (i in childIndices) {
        updateNodeValue(model.getChild(parent, i))
      }
    }

    override fun treeNodesInserted(e: TreeModelEvent) {
      val treePath = e.treePath ?: return
      val parent = treePath.lastPathComponent
      updateNodeValue(parent) // leaf state update
      val childIndices = e.childIndices ?: return
      insertChildren(parent, childIndices)
    }

    override fun treeNodesRemoved(e: TreeModelEvent) {
      val treePath = e.treePath ?: return
      val parent = treePath.lastPathComponent
      updateNodeValue(parent) // leaf state update
      val childIndices = e.childIndices ?: return
      for (i in childIndices.reversed()) { // reversed() is a must for consistency (indices remain valid as children are being removed)
        removeChild(parent, i)
      }
    }

    override fun treeStructureChanged(e: TreeModelEvent) {
      val treePath = e.treePath
      if (treePath == null || treePath.parentPath == null) {
        updateNodeStructure(SuperRoot)
        return
      }
      updateNodeStructure(treePath.lastPathComponent)
    }
  }
}

private sealed class ModelUpdateRequest

private data class ModelChildrenLoaded(val parentId: Long, val children: List<ModelChildDescriptor>) : ModelUpdateRequest()

private data class ModelChildDescriptor(val id: Long, val modelNode: Any)

private data class ModelNodeAdded(val parentId: Long, val index: Int, val nodeId: Long, val modelNode: Any) : ModelUpdateRequest()

private data class ModelNodeUpdated(val nodeId: Long, val modelNode: Any) : ModelUpdateRequest()

private data class ModelChildrenRemoved(val parentId: Long) : ModelUpdateRequest()

private data class ModelChildRemoved(val parentId: Long, val index: Int) : ModelUpdateRequest()

private data class ModelActionStatesUpdated(
  val actionState: ProjectViewActionState,
) : ModelUpdateRequest()

private data class LegacyProjectViewNode(
  val id: Long,
  val parentId: Long?,
  val modelNode: Any,
  var childrenState: ChildrenState = ChildrenState.NOT_LOADED,
)

private enum class ChildrenState {
  NOT_LOADED,
  LOADING,
  LOADED
}

private val TreeModelEvent.model: TreeModel
  get() = source as TreeModel

private val LOG = logger<LegacyBackendProjectViewPane>()
