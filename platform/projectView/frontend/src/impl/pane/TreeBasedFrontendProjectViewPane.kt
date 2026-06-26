// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.impl.pane

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.ui.UISettings
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
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.pane.NestingRuleDTO
import com.intellij.platform.projectView.pane.ProjectViewPaneSettingsStateDTO
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.pane.ProjectViewPaneOptionDTO
import com.intellij.platform.projectView.actions.ProjectViewOptionMenuUpdater
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewSettingsStateEvent
import com.intellij.platform.projectView.pane.ProjectViewChildRemoved
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewChildrenRemoved
import com.intellij.platform.projectView.pane.ProjectViewClearStateEvent
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
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
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.stripe.ErrorStripe
import com.intellij.ui.stripe.ErrorStripePainter
import com.intellij.ui.stripe.TreeUpdater
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.asDisposable
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.tree.TreeUtil
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

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
  private val expandRequests = Channel<ExpandRequest>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val treeExpander = ProjectViewTreeExpander(tree, expandRequests)

  private val optionSupport = ActionSupport()
  
  private inner class ContentPanel(content: JComponent) : SimpleToolWindowPanel(true), UiDataProvider {
    init {
      setContent(content)
      ClientProperty.put(this, FileEditorManagerKeys.OPEN_IN_PREVIEW_TAB, true)
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

  private val autoscrollToSourceHandler = MyAutoscrollToSourceHandler(project)

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
    autoscrollToSourceHandler.install(tree)
    tree.launchOnShow("expand requests") {
      expandRequests.consumeAsFlow().collectLatest { expandRequest ->
        expand(expandRequest)
      }
    }
  }

  private fun sendRequest(request: ProjectViewPaneRequest) {
    check(_requestChannel.trySend(request).isSuccess)
  }

  override suspend fun manage() {
    coroutineScope {
      launch(CoroutineName("autoscrollToSourceHandler")) {
        autoscrollToSourceHandler.manage()
      }
      if (Registry.`is`("error.stripe.enabled", defaultValue = true)) {
        launch(CoroutineName("error stripe") + Dispatchers.UI) {
          Disposer.register(asDisposable(), MyTreeUpdater(ErrorStripePainter(true), scrollPane, tree))
          awaitCancellation()
        }
      }
    }
  }

  override fun getOptionSupport(): ProjectViewActionSupport = optionSupport

  override fun applyStateChange(event: ProjectViewPaneStateEvent) {
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

  suspend fun selectNode(nodePath: ProjectViewNodePath) {
    withContext(Dispatchers.UI) {
      LOG.debug { "Resolving $nodePath" }
      val treePath = awaitNodePath(nodePath.nodeIds.last())
      LOG.debug { "Resolved $nodePath => $treePath" }
      TreeUtil.selectPath(tree, treePath)
      LOG.debug { "Selected $treePath" }
    }
  }

  private suspend fun expand(expandRequest: ExpandRequest) {
    try {
      LOG.debug { "Executing the expand request $expandRequest" }
      tree.suspendExpandCollapseAccessibilityAnnouncements()
      coroutineScope {
        for (path in expandRequest.paths) {
          launch {
            expand(path)
          }
        }
      }
      for (path in expandRequest.paths) {
        tree.fireAccessibleTreeExpanded(path)
      }
      LOG.debug { "Executed the expand request $expandRequest" }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("An error has occurred while executing the expand request $expandRequest", e)
    }
    finally {
      tree.resumeExpandCollapseAccessibilityAnnouncements()
    }
  }

  private suspend fun expand(path: TreePath) {
    lateinit var started: ComparableTimeMark
    LOG.debug {
      started = TimeSource.Monotonic.markNow()
      "Expanding $path and its descendants"
    }
    if (!tree.isVisible(path)) {
      LOG.trace { "Not expanding $path because it's not visible, assuming the user has canceled expanding" }
      return
    }
    // First, fast-path bulk expand everything already loaded to avoid excessive flickering and/or freezes.
    val allNonLeafDescendants = allExpandableDescendants(path)
    LOG.trace { "Expanding ${allNonLeafDescendants.size} already-loaded non-leaf descendants of $path" }
    tree.expandPaths(allNonLeafDescendants)
    LOG.trace { "Expanded ${allNonLeafDescendants.size} already-loaded non-leaf descendants of $path" }
    // Now load all missing children and expand them.
    expandNotLoaded(path, 0)
    LOG.debug {
      "Expanded $path and its descendants, took ${started.elapsedNow()}"
    }
  }

  private fun allExpandableDescendants(path: TreePath): List<TreePath> {
    val result = mutableListOf<TreePath>()
    collectAllExpandableDescendants(path, result, 0)
    return result
  }

  private fun collectAllExpandableDescendants(
    path: TreePath,
    result: MutableList<TreePath>,
    depth: Int,
  ) {
    val node = path.lastPathComponent
    if (treeModel.isLeaf(node)) return
    // For depth == 0 it means that the user explicitly requested to expand this.
    if (depth > 0 && (node as? Node)?.projectViewNode?.isIncludedInExpandAll() == false) {
      LOG.trace { "Won't expand $node because isIncludedInExpandAll == false" }
      return
    }
    result += path
    val childCount = treeModel.getChildCount(node)
    for (i in 0 until childCount) {
      val childNode = treeModel.getChild(node, i)
      val childPath = path.pathByAddingChild(childNode)
      collectAllExpandableDescendants(childPath, result, depth + 1)
    }
  }

  private suspend fun expandNotLoaded(path: TreePath, depth: Int) {
    // Even with tracing enabled, we don't want to spam messages about the nodes that were already expanded at the fast-path bulk stage.
    val doTraceLogging = LOG.isTraceEnabled && !tree.isExpanded(path) && !treeModel.isLeaf(path.lastPathComponent)
    if (doTraceLogging) {
      LOG.trace { "Expanding not yet loaded descendants of $path" }
    }
    val node = path.lastPathComponent as? Node
    if (node == null) {
      if (doTraceLogging) {
        LOG.trace {
          "Not expanding ${path.lastPathComponent} because it itself is not loaded yet" +
          " (recursive expand for cached nodes not supported)"
        }
      }
      return
    }
    if (treeModel.isLeaf(node)) {
      if (doTraceLogging) {
        LOG.trace { "Not expanding $node because it's a leaf" }
      }
      return
    }
    // For depth == 0 it means that the user explicitly requested to expand this.
    if (depth > 0 && !node.projectViewNode.isIncludedInExpandAll()) {
      if (doTraceLogging) {
        LOG.trace { "Won't expand $node because isIncludedInExpandAll == false" }
      }
      return
    }
    if (!tree.isVisible(path)) {
      if (doTraceLogging) {
        LOG.trace { "Won't expand $node because it's no longer visible (the user has collapsed an ancestor)" }
      }
      return
    }
    if (doTraceLogging) {
      LOG.trace { "Expanding $path" }
    }
    tree.expandPath(path) // could be already expanded during the first pass, but it'll be a quick no-op then
    if (doTraceLogging) { // to avoid spamming confusing trace messages in the already-loaded case
      LOG.trace { "Loading the children of $path" }
    }
    val children = awaitNodeChildren(node) {
      val isVisible = tree.isVisible(path) // wait while the node is visible, otherwise cancel because the user has collapsed an ancestor
      if (doTraceLogging && !isVisible) {
        LOG.trace { "Cancelling expanding of $node because it's no longer visible (the user has collapsed an ancestor)" }
      }
      isVisible
    }
    if (children == null) {
      if (doTraceLogging) {
        LOG.trace { "Did not load the children of $node (the reason should be in the messages above)" }
      }
      return
    }
    if (doTraceLogging) { // to avoid spamming confusing trace messages in the already-loaded case
      LOG.trace { "Loaded the children of $path" }
    }
    for (child in children) {
      expandNotLoaded(path.pathByAddingChild(child), depth + 1)
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

  private suspend fun awaitNodeChildren(node: Node, condition: () -> Boolean): List<Node>? {
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

private class Node(
  model: ProjectViewNodeModel,
) : DefaultMutableTreeNode(model), TreeNodeWithPresentation, PathElementIdProvider {
  val projectViewNode: ProjectViewNodeModelImpl
    get() = userObject as ProjectViewNodeModelImpl
  
  var isChildrenLoaded: Boolean = false
  
  val id: Long
    get() = projectViewNode.id

  override val presentation: TreeNodePresentationImpl
    get() = projectViewNode.presentation as TreeNodePresentationImpl

  override fun isLeaf(): Boolean {
    return projectViewNode.presentation.isLeaf
  }

  override fun getPathElementId(): String = presentation.mainText

  override fun toString(): String = "{[${projectViewNode.id}] ${projectViewNode.presentation.mainText}}"
}

private class ProjectViewTreeExpander(tree: Tree, private val expandRequests: SendChannel<ExpandRequest>) : DefaultTreeExpander(tree) {
  override fun isExpandAllVisible(): Boolean {
    return Registry.`is`("ide.project.view.expand.all.action.visible") && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun isExpandAllEnabled(): Boolean {
    return super.isExpandAllEnabled() && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun expandSelected(tree: JTree) {
    val result = expandRequests.trySend(ExpandRequest(tree.selectionPaths.toList()))
    check(!result.isFailure)
  }

  override fun collapseAll(tree: JTree, strict: Boolean, keepSelectionLevel: Int) {
    super.collapseAll(tree, false, keepSelectionLevel)
  }
}

private data class ExpandRequest(val paths: List<TreePath>)

private class MyAutoscrollToSourceHandler(private val project: Project) : AutoScrollToSourceHandler() {
  override fun isAutoScrollMode(): Boolean {
    return ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE.isOn() || ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB.isOn()
  }

  override fun setAutoScrollMode(state: Boolean) {
    ProjectViewActionSupport.getInstance(project).requestOptionValueChange(ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE, state)
  }

  private fun ProjectViewPaneOptionDTO.isOn(): Boolean =
    ProjectViewActionSupport.getInstance(project).getActionState()?.optionStates?.get(this)?.isSelected == true

  suspend fun manage() {
    // sync the setting from the backend, because it's used on the frontend
    ProjectViewActionSupport.getInstance(project).getActionStateFlow().map { 
      it?.optionStates?.get(ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB)?.isSelected == true
    }.distinctUntilChanged()
      .collectLatest { 
        UISettings.getInstance().openInPreviewTabIfPossible = it
      }
  }
}

private class MyTreeUpdater(
  errorStripePainter: ErrorStripePainter,
  scrollPane: JScrollPane,
  private val tree: Tree,
) : TreeUpdater<ErrorStripePainter>(errorStripePainter, scrollPane, tree) {
  override fun update(painter: ErrorStripePainter?, index: Int, node: Any?) {
    super.update(painter, index, getErrorStripe(node, index))
  }

  private fun getErrorStripe(node: Any?, index: Int): ErrorStripe? {
    if (node !is Node) return null
    if (node.projectViewNode.isDirectory() && tree.isExpanded(index)) return null
    val textAttributesKey = node.presentation.textAttributesKey ?: return null
    val textAttributes = EditorColorsManager.getInstance().schemeForCurrentUITheme.getAttributes(textAttributesKey) ?: return null
    val errorStripeColor = textAttributes.errorStripeColor ?: return null
    return ErrorStripe.create(errorStripeColor, 1)
  }
}

private val LOG = logger<TreeBasedFrontendProjectViewPane>()
