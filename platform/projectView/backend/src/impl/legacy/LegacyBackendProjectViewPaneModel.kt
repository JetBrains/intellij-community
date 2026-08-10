// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.projectView.backend.impl.legacy

import com.intellij.ide.ActivityTracker
import com.intellij.ide.dnd.DnDEventImpl
import com.intellij.ide.dnd.DnDManagerImpl
import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewDropTarget
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.projectView.actions.legacyProjectViewOption
import com.intellij.platform.projectView.impl.DataContextCutCopyPasteDeleteHandler
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewDnDOptions
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneCutCopyPasteDeleteHandler
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneDnDHandler
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SelectByContext
import com.intellij.platform.projectView.pane.SelectByEditor
import com.intellij.platform.projectView.pane.SelectInRequest
import com.intellij.platform.projectView.pane.SuperRoot
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.platform.projectView.pane.projectViewNodePath
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.platform.projectView.settings.allProjectViewPaneOptions
import com.intellij.platform.projectView.settings.toLegacySortKey
import com.intellij.platform.projectView.settings.toSettingValue
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.LoadingNode
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.tree.buildTreeNodeDescriptorPresentation
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

internal class LegacyBackendProjectViewPaneProvider : ProjectViewPaneProvider {
  override suspend fun createPanes(project: Project): List<ProjectViewPaneModel> {
    return project.service<LegacyBackendProjectViewPaneService>().createPanes()
  }
}

private val PANES_WITH_NEW_IMPLEMENTATIONS = setOf(ProjectViewPane.ID, "PackagesPane")

@Service(Service.Level.PROJECT)
private class LegacyBackendProjectViewPaneService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  suspend fun createPanes(): List<ProjectViewPaneModel> {
    withContext(Dispatchers.UI) {
      (ProjectView.getInstance(project) as ProjectViewImpl).setupBackend()
    }
    return AbstractProjectViewPane.EP.getExtensions(project).flatMap { legacyPane ->
      createLegacyPanes(legacyPane)
    }
  }

  private fun createLegacyPanes(legacyPane: AbstractProjectViewPane): Iterable<ProjectViewPaneModel> {
    if (legacyPane.id in PANES_WITH_NEW_IMPLEMENTATIONS) return emptyList()
    val stateManager = AbstractProjectViewPaneStateManager(project, coroutineScope.childScope("LegacyBackendProjectViewPane: $legacyPane"), legacyPane)
    val subIds = legacyPane.subIds
    if (subIds.isEmpty()) {
      return listOf(LegacyBackendProjectViewPaneModel(
        project = project,
        legacyPaneManager = stateManager,
        subId = null,
        addSelectInTargetDescriptors = true,
      ))
    }
    else {
      return subIds.map { subId ->
        LegacyBackendProjectViewPaneModel(
          project = project,
          legacyPaneManager = stateManager,
          subId = subId,
          // Scope panes share the common select in target, so we only return it once.
          addSelectInTargetDescriptors = subId == subIds.first(),
        )
      }
    }
  }
}

private class LegacyBackendProjectViewPaneModel(
  override val project: Project,
  private val legacyPaneManager: AbstractProjectViewPaneStateManager,
  private val subId: String?,
  private val addSelectInTargetDescriptors: Boolean,
) : ProjectViewPaneModel {
  private val id = projectViewPaneId(if (subId == null) legacyPaneManager.id else "${legacyPaneManager.id}:$subId")

  override val cutCopyPasteDeleteHandler: ProjectViewPaneCutCopyPasteDeleteHandler = MyCutCopyPasteDeleteHandler()

  override val dndHandler: ProjectViewPaneDnDHandler
    get() = object : ProjectViewPaneDnDHandler {
      override suspend fun performInternalDnD(sourceIDs: List<Long>, targetID: Long, options: ProjectViewDnDOptions) {
        withContext(Dispatchers.EDT) {
          legacyPaneManager.performInternalDnD(sourceIDs, targetID, options)
        }
      }
    }

  override suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor = builder.run {
    if (legacyPaneManager.legacyPane.isDefaultPane(project)) {
      setDefault(true)
    }
    if (addSelectInTargetDescriptors) {
      val selectInTarget = legacyPaneManager.selectInTarget
      addSelectInTarget(
        id = legacyPaneManager.id, // For the PV it's the same as selectInTarget.minorViewId, but that one is declared nullable.
        presentableName = selectInTarget.toString(),
        weight = selectInTarget.weight
      )
    }
    build(
      id = id,
      presentableName = if (subId == null) legacyPaneManager.legacyPane.title else legacyPaneManager.legacyPane.getPresentableSubIdName(subId),
      order = legacyPaneManager.legacyPane.weight,
    )
  }

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    coroutineScope {
      launch(CoroutineName("Manage pane $id")) {
        legacyPaneManager.subId = subId
        legacyPaneManager.manageState(id, builder)
      }
    }
  }

  override suspend fun setPaneSelected(
    isSelected: Boolean,
    options: ProjectViewPaneSelectionOptions,
  ) {
    if (isSelected) {
      legacyPaneManager.awaitInitialization()
      withContext(Dispatchers.UI) {
        legacyPaneManager.setSelected()
      }
    }
  }

  override suspend fun loadChildren(
    parentId: Long,
    options: ProjectViewPaneLoadChildrenOptions,
  ) {
    withContext(Dispatchers.UI) {
      legacyPaneManager.loadChildren(parentId)
    }
  }

  override suspend fun navigate(
    nodeId: Long,
    options: ProjectViewPaneNavigateOptions,
  ) {
    withContext(Dispatchers.UI) {
      legacyPaneManager.navigate(nodeId, options.requestFocus)
    }
  }

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
    withContext(Dispatchers.UI) {
      legacyPaneManager.changeOptionValue(option, newValue)
    }
  }

  override suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey) {
    withContext(Dispatchers.UI) {
      legacyPaneManager.changeSortKey(sortKeyValue)
    }
  }

  override suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue) {
    withContext(Dispatchers.UI) {
      legacyPaneManager.changeFileNesting(fileNestingValue)
    }
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val selectedIds = snapshot[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] ?: return
    legacyPaneManager.uiDataSnapshot(sink, selectedIds)
  }

  override fun getDataContext(nodeIds: List<Long>): DataContext {
    return selectionDataContext(nodeIds)
  }

  /**
   * The data context the legacy pane itself would have for this selection.
   *
   * Unlike the new pane model, the legacy wrapper stays entirely data-context based: pushing the selection
   * into the legacy tree and asking the pane for its snapshot is exactly what [uiDataSnapshot] does, and it
   * gives all the keys the copy/paste/delete handlers need, including the pane's own
   * [com.intellij.openapi.actionSystem.PlatformDataKeys.DELETE_ELEMENT_PROVIDER] choice.
   */
  @RequiresEdt
  private fun selectionDataContext(nodeIds: List<Long>): DataContext {
    return CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[CommonDataKeys.PROJECT] = project
      legacyPaneManager.uiDataSnapshot(sink, nodeIds)
    }
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    return when (selectInRequest) {
      is SelectByContext -> {
        findNodeForContext(selectInRequest)
      }
      is SelectByEditor -> {
        findNodeForEditor(selectInRequest)
      }
    }
  }

  private suspend fun findNodeForContext(selectByContext: SelectByContext): ProjectViewNodePath? {
    val target = legacyPaneManager.selectInTarget
    val context = selectByContext.context
    if (!readAction { target.canSelect(context) }) return null
    return selectAndGetPath {
      LOG.debug { "[$id] Selecting using the context $context" }
      target.selectIn(context, false) // requestFocus doesn't matter because it's backend code
    }
  }

  private suspend fun findNodeForEditor(selectByEditor: SelectByEditor): ProjectViewNodePath? {
    return selectAndGetPath {
      val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
      if (selectByEditor.considerOnlyLastFocusedEditor && AppMode.isMonolith()) { // in remdev, "last focused" isn't very meaningful
        LOG.debug { "[$id] Selecting using the last focused editor because the editor choice = $selectByEditor, is monolith = ${AppMode.isMonolith()}" }
        impl.selectOpenedFileUsingLastFocusedEditor()
      }
      else {
        LOG.debug { "[$id] Selecting using the selected editor because the editor choice = $selectByEditor, is monolith = ${AppMode.isMonolith()}" }
        impl.selectOpenedFile()
      }
    }
  }

  private suspend fun selectAndGetPath(select: () -> Unit): ProjectViewNodePath? {
    return withContext(Dispatchers.EDT) {
      val tree = legacyPaneManager.legacyPane.tree ?: return@withContext null
      // Even with the REAL_SELECTION_IN_PROGRESS hack,
      // there's a case when this listener prevents us from detecting the "real" selection:
      // 1. We set the selection to null.
      // 2. The listener resets it back to what it was (isReal = false).
      // 3. The "real" selection by chance matches the existing one.
      // => no callback with isReal = true, because there's no change. The frontend keeps waiting until it times out.
      ClientProperty.put(tree, RestoreSelectionListener.DISABLED, true)
      tree.selectionPath = null
      // suspendCancellableCoroutine makes listener deregistration incredibly tricky, so let's put it outside it
      val captureSelection = AtomicReference<((TreePath?) -> Unit)?>(null)
      val selectionListener = TreeSelectionListener { event ->
        captureSelection.load()?.invoke(event.newLeadSelectionPath)
      }
      val selectedPath = try {
        tree.addTreeSelectionListener(selectionListener)
        suspendCancellableCoroutine { continuation ->
          captureSelection.store { selectionPath ->
            val isRealSelection = ClientProperty.isTrue(tree, AbstractProjectViewPane.REAL_SELECTION_IN_PROGRESS)
            LOG.debug { "Selected (is real = $isRealSelection) $selectionPath" }
            if (isRealSelection) {
              continuation.resume(selectionPath)
            }
          }
          select()
          continuation.invokeOnCancellation { captureSelection.store(null) }
        }
      }
      finally {
        tree.removeTreeSelectionListener(selectionListener)
      }
      selectedPath?.load()
    }
  }

  private suspend fun TreePath.load(): ProjectViewNodePath? {
    val ids = loadIds(this) ?: return null
    return projectViewNodePath(id, ids)
  }

  private suspend fun loadIds(path: TreePath): List<Long>? {
    if (path.parentPath == null) {
      val id = legacyPaneManager.loadNode(SUPER_ROOT_ID, path.lastPathComponent)?.id ?: return null
      return listOf(id)
    }
    else {
      val parentIds = loadIds(path.parentPath) ?: return null
      val id = legacyPaneManager.loadNode(parentIds.last(), path.lastPathComponent)?.id ?: return null
      return parentIds + id
    }
  }

  private inner class MyCutCopyPasteDeleteHandler : ProjectViewPaneCutCopyPasteDeleteHandler {
    override suspend fun performCopy(nodeIds: List<Long>) {
      withContext(Dispatchers.EDT) {
        DataContextCutCopyPasteDeleteHandler.copy(selectionDataContext(nodeIds))
      }
    }

    override suspend fun performCut(nodeIds: List<Long>) {
      withContext(Dispatchers.EDT) {
        DataContextCutCopyPasteDeleteHandler.cut(selectionDataContext(nodeIds))
      }
    }

    override suspend fun performPaste(nodeIds: List<Long>) {
      withContext(Dispatchers.EDT) {
        DataContextCutCopyPasteDeleteHandler.paste(selectionDataContext(nodeIds))
      }
    }

    override suspend fun performDelete(nodeIds: List<Long>) {
      withContext(Dispatchers.EDT) {
        DataContextCutCopyPasteDeleteHandler.delete(selectionDataContext(nodeIds))
      }
    }
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

  val selectInTarget = legacyPane.createSelectInTarget()

  private val activePanes = MutableStateFlow<Set<ProjectViewPaneId>>(emptySet())

  private val subIdFlow = MutableStateFlow<String?>(null)
  
  private var modelUpdateChannel: Channel<ModelUpdateRequest>? = null

  private var nextNodeId = SUPER_ROOT_ID
  
  private val nodeById = hashMapOf<Long, LegacyProjectViewNode>()

  private val nodeByModelNode = hashMapOf<Any, LegacyProjectViewNode>()

  private val updateEpochFlow = MutableStateFlow(0L)
  
  private lateinit var treeModel: AsyncTreeModel
  
  private val initDeferred = CompletableDeferred<Unit>()
  
  init {
    coroutineScope.launch(CoroutineName("AbstractProjectViewPaneStateManager")) {
      activePanes.first { it.isNotEmpty() }
      manageLegacyBackend()
    }
  }

  suspend fun loadNode(parentId: Long, modelChild: Any): LegacyProjectViewNode? {
    return withContext(Dispatchers.UI) { // our data structures are EDT-only and updates happen there too
      var updateEpoch = updateEpochFlow.value
      var result: LegacyProjectViewNode? = null
      while (true) {
        val child = nodeByModelNode[modelChild]
        // Already loaded? Just return it.
        if (child != null) {
          result = child
          break
        }
        // The parent doesn't exist? Can't do anything then.
        val parent = nodeById[parentId] ?: break
        // The children are already loaded and the child isn't there? Can't do anything. Likely it was just removed.
        if (parent.childrenState == ChildrenState.LOADED) break
        // The children not loaded? Request and wait.
        loadChildren(parentId)
        updateEpoch = updateEpochFlow.first { it > updateEpoch }
      }
      result
    }
  }

  suspend fun manageState(paneId: ProjectViewPaneId, builder: ProjectViewPaneStateBuilder) {
    activePanes.update { it + paneId }
    try {
      initDeferred.await()
      withContext(Dispatchers.UI) {
        val currentSubId = subId
        LOG.debug { "Updating state for pane id = $id, subId = $currentSubId" }
        val modelUpdateChannel = Channel<ModelUpdateRequest>(capacity = Channel.UNLIMITED)
        this@AbstractProjectViewPaneStateManager.modelUpdateChannel = modelUpdateChannel
        sendCurrentState()
        try {
          for (request in modelUpdateChannel) {
            LOG.trace { "Applying state update for pane $id: $request" }
            updateState(builder, request)
            updateEpochFlow.update { it + 1 }
          }
        }
        finally {
          this@AbstractProjectViewPaneStateManager.modelUpdateChannel = null
          LOG.debug { "Done updating state for pane id = $id, subId = $currentSubId" }
        }
      }
    }
    finally {
      activePanes.update { it - paneId }
    }
  }

  private fun sendCurrentState() {
    updateActionStates()
    sendCurrentTreeState()
  }

  private fun sendCurrentTreeState() {
    val modelRoot = treeModel.root ?: return
    val root = nodeByModelNode[modelRoot]
    checkNotNull(root) { "The root exists, but not registered in nodeByModelNode" }
    handleChildrenLoaded(SUPER_ROOT_ID, listOf(root))
    sendChildrenRecursivelyIfLoaded(root.id)
  }

  private fun sendChildrenRecursivelyIfLoaded(id: Long) {
    val node = nodeById[id] ?: return
    if (node.childrenState != ChildrenState.LOADED) return
    val children = (0 until treeModel.getChildCount(node.modelNode)).asSequence().map { i -> 
      treeModel.getChild(node.modelNode, i)
    }.mapNotNull { modelChild ->
      nodeByModelNode[modelChild]
    }.toList()
    handleChildrenLoaded(id, children)
    for (child in children) {
      sendChildrenRecursivelyIfLoaded(child.id)
    }
  }

  private suspend fun updateState(builder: ProjectViewPaneStateBuilder, request: ModelUpdateRequest) {
    when (request) {
      is ModelChildrenLoaded -> {
        builder.setNodeChildren(request.parentId, request.children.map { createNodeModel(it.id, it.modelNode) })
      }
      is ModelNodeAdded -> {
        builder.addNode(request.parentId, request.index, createNodeModel(request.nodeId, request.modelNode))
      }
      is ModelNodeUpdated -> {
        builder.updateNode(createNodeModel(request.nodeId, request.modelNode))
      }
      is ModelChildrenRemoved -> {
        builder.removeNodeChildren(request.parentId)
      }
      is ModelChildRemoved -> {
        builder.removeNodeChild(request.parentId, request.index)
      }
      is ModelSettingStateUpdated -> {
        builder.updateSettingsState { settings ->
          for ((option, state) in request.optionStates) {
            settings.setOptionState(option, state.isSelected, state.isEnabled, state.isAlwaysVisible)
          }
          settings.setSortKey(request.sortKey)
          settings.setAvailableSortKeys(request.availableSortKeys)
          settings.setFileNesting(
            request.fileNesting.isFileNestingOn,
            request.fileNesting.isFileNestingAvailable,
            request.fileNesting.activeRules,
            request.fileNesting.defaultRules,
          )
        }
      }
    }
  }

  private suspend fun createNodeModel(id: Long, node: Any): ProjectViewNodeModel {
    val isLeaf = withContext(Dispatchers.UI) {
      treeModel.isLeaf(node)
    }
    return readAction {
      buildProjectViewNodeModel(id, node) { nodeBuilder ->
        nodeBuilder.buildPresentation { presentationBuilder ->
          buildNodePresentation(node, presentationBuilder, isLeaf)
        }
        nodeBuilder.setCanNavigate(canNavigate(node))
        nodeBuilder.setCanNavigateToSource(canNavigateToSource(node))
        nodeBuilder.setIncludedInExpandAll(isIncludedInExpandAll(node))
        nodeBuilder.setIsDirectory(isDirectory(node))
        nodeBuilder.setExpandOnDoubleClick(isExpandOnDoubleClick(node))
      }
    }
  }

  private fun buildNodePresentation(node: Any, builder: TreeNodePresentationBuilder, isLeaf: Boolean): TreeNodePresentation {
    builder.setLeaf(isLeaf)
    return when (val userObject = TreeUtil.getUserObject(node)) {
      is PresentableNodeDescriptor<*> -> {
        buildTreeNodeDescriptorPresentation(userObject, builder)
      }
      else -> {
        builder.apply {
          setMainText(userObject.toString())
        }.build()
      }
    }
  }

  private suspend fun manageLegacyBackend() {
    coroutineScope {
      withContext(Dispatchers.UI) {
        var treeModelListener: MyTreeModelListener? = null
        try {
          LOG.debug { "Managing the legacy PV pane $id" }
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
          initDeferred.complete(Unit)
          awaitCancellation()
        }
        finally {
          if (treeModelListener != null) {
            treeModel.removeTreeModelListener(treeModelListener)
          }
          Disposer.dispose(legacyPane)
          LOG.debug { "Disposed the legacy PV pane $id" }
        }
      }
    }
  }

  suspend fun awaitInitialization() {
    initDeferred.await()
  }

  fun setSelected() {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    impl.changeView(id)
    updateActionStates()
  }

  @RequiresReadLock
  private fun canNavigate(node: Any): Boolean = (TreeUtil.getUserObject(node) as? Navigatable?)?.canNavigate() == true

  @RequiresReadLock
  private fun canNavigateToSource(node: Any): Boolean = (TreeUtil.getUserObject(node) as? Navigatable?)?.canNavigateToSource() == true
  
  private fun isIncludedInExpandAll(node: Any): Boolean = (TreeUtil.getUserObject(node) as? AbstractTreeNode<*>)?.isIncludedInExpandAll != false

  private fun isDirectory(node: Any): Boolean = (TreeUtil.getUserObject(node) as? AbstractTreeNode<*>) is PsiDirectoryNode

  private fun isExpandOnDoubleClick(node: Any): Boolean = (TreeUtil.getUserObject(node) as? NodeDescriptor<*>)?.expandOnDoubleClick() != false

  suspend fun navigate(id: Long, requestFocus: Boolean) {
    val node = nodeById[id] ?: return
    val navigatable = TreeUtil.getUserObject(node.modelNode) as? Navigatable? ?: return
    val navigationRequest = readAction { navigatable.navigationRequest() } ?: return
    NavigationService.getInstance(project).navigate(
      request = navigationRequest,
      options = NavigationOptions.defaultOptions()
        .requestFocus(requestFocus),
    )
  }

  private fun loadInitialState() {
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

  fun loadChildren(parentId: Long) {
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
    val result = modelUpdateChannel?.trySend(update)
    check(result == null || result.isSuccess || result.isClosed)
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
    handleChildrenLoaded(parentId, newNodes)
  }

  private fun handleChildrenLoaded(
    parentId: Long,
    newNodes: List<LegacyProjectViewNode>,
  ) {
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

  fun changeOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
    val legacyOption = legacyProjectViewOption(project, option)
    legacyOption.isSelected = newValue
    updateActionStates()
  }

  fun changeSortKey(sortKey: ProjectViewPaneSortKey) {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    impl.setSortKey(id, sortKey.toLegacySortKey())
    updateActionStates()
  }

  fun changeFileNesting(fileNesting: ProjectViewPaneFileNestingValue) {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    impl.setUseFileNestingRules(fileNesting.isFileNestingOn)
    ProjectViewFileNestingService.getInstance().setRules(fileNesting.nestingRules)
    impl.currentProjectViewPane?.updateFromRoot(true, ProjectViewUpdateCause.SETTINGS)
    updateActionStates()
  }

  private fun updateActionStates() {
    val impl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    val updatedOptionStates = allProjectViewPaneOptions().associateWith { option ->
      val legacyOption = legacyProjectViewOption(project, option)
      OptionState(
        isSelected = legacyOption.isSelected,
        isEnabled = legacyOption.isEnabled,
        isAlwaysVisible = legacyOption.isAlwaysVisible,
      )
    }
    val sortKey = impl.getSortKey(id).toSettingValue()
    val availableSortKeys = NodeSortKey.entries.filter { impl.isSortKeySupported(id, it) }.map { it.toSettingValue() }
    val updatedFileNestingState = FileNesting(
      isFileNestingOn = ProjectViewState.getInstance(project).useFileNestingRules,
      isFileNestingAvailable = impl.currentProjectViewPane?.isFileNestingEnabled == true,
      ProjectViewFileNestingService.getInstance().getRules(),
      ProjectViewFileNestingService.getInstance().getDefaultRules()
    )
    LOG.debug { "Updated option states: $updatedOptionStates" }
    handleModelUpdate(ModelSettingStateUpdated(
      optionStates = updatedOptionStates,
      sortKey = sortKey,
      availableSortKeys = availableSortKeys,
      fileNesting = updatedFileNestingState,
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

  fun performInternalDnD(sourceIDs: List<Long>, targetID: Long, options: ProjectViewDnDOptions) {
    ActivityTracker.getInstance().inc() // we need the new selection to be reflected in the data context
    val manager = DnDManagerImpl.getInstance() as? DnDManagerImpl ?: return
    val tree = legacyPane.tree ?: return
    tree.selectionPaths = sourceIDs.mapNotNull { nodeById[it]?.treePath() }.toTypedArray()
    val dndBean = legacyPane.dragSource?.startDragging(options.action, Point()) ?: return
    val targetPath = nodeById[targetID]?.treePath() ?: return
    (legacyPane.dropTarget as? ProjectViewDropTarget?)
      ?.doDrop(DnDEventImpl(manager, options.action, dndBean.attachedObject, Point()), targetPath)
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
      if (treePath?.parentPath == null) {
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

private data class ModelSettingStateUpdated(
  val optionStates: Map<ProjectViewPaneOption, OptionState>,
  val sortKey: ProjectViewPaneSortKey,
  val availableSortKeys: List<ProjectViewPaneSortKey>,
  val fileNesting: FileNesting,
) : ModelUpdateRequest()

private data class OptionState(
  val isSelected: Boolean,
  val isEnabled: Boolean,
  val isAlwaysVisible: Boolean,
)

private data class FileNesting(
  val isFileNestingOn: Boolean,
  val isFileNestingAvailable: Boolean,
  val activeRules: List<ProjectViewFileNestingService.NestingRule>,
  val defaultRules: List<ProjectViewFileNestingService.NestingRule>,
)

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

private val LOG = logger<LegacyBackendProjectViewPaneModel>()
