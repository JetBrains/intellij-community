// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.util.treeView.CachedTreePresentation
import com.intellij.ide.util.treeView.CachedTreePresentationSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.LoadingNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isPending
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath

internal class TreeSwingModelFactoryImpl : TreeSwingModelFactory {
  override fun createTreeSwingModel(coroutineScope: CoroutineScope, viewModel: TreeViewModel): TreeSwingModel =
    TreeSwingModelImpl(coroutineScope, viewModel)
}

private class TreeSwingModelImpl(
  parentScope: CoroutineScope,
  override val viewModel: TreeViewModel,
) : TreeSwingModel, CachedTreePresentationSupport {
  private val treeScope = parentScope.childScope("Root of $this", Dispatchers.EDT)
  private val listeners = CopyOnWriteArrayList<TreeModelListener>()
  private var root: Node? = null
  // These things must be thread-safe because of the "cancellation can happen anywhere, anytime" thing.
  private val nodes = ConcurrentHashMap<TreeNodeViewModel, Node>()
  private val nodeLoadedListeners = CopyOnWriteArrayList<NodeLoadedListener>()
  private var scrollRequest: TreeNodeViewModel? = null
  private var cachedPresentation: CachedTreePresentation? = null

  override var showLoadingNode: Boolean = true

  private interface NodeLoadedListener {
    fun nodePublished(node: Node)
  }

  init {
    treeScope.launch(CoroutineName("Root updates of $this")) {
      viewModel.root.collect { rootViewModel ->
        val root = rootViewModel?.let {
          findOrLoadNode(null, rootViewModel)
        }
        if (this@TreeSwingModelImpl.root != root) {
          this@TreeSwingModelImpl.root?.dispose()
          this@TreeSwingModelImpl.root = root
          if (root != null) {
            cachedPresentation?.rootLoaded(root.viewModel)
          }
          treeStructureChanged(root)
          if (root != null) {
            markPublished(root)
          }
        }
      }
    }
    treeScope.launch(CoroutineName("Selection updates of $this")) {
      viewModel.selection.collectLatest { selection ->
        val event = TreeSwingModelSelectionEvent(this@TreeSwingModelImpl, selection.map { it.path() }.toTypedArray())
        fireSwingTreeModelEvent {
          selectionChanged(event)
        }
      }
    }
    treeScope.launch(CoroutineName("Scroll requests of $this")) {
      viewModel.scrollEvents.collectLatest { node ->
        scrollRequest = node
        tryToScroll()
      }
    }
  }

  private fun tryToScroll() {
    val nodeViewModel = scrollRequest ?: return
    val node = nodes[nodeViewModel] ?: return
    if (node.lifecycle != NodeLifecycle.PUBLISHED) return
    val event = TreeSwingModelScrollEvent(this@TreeSwingModelImpl, nodeViewModel.path())
    fireSwingTreeModelEvent {
      scrollRequested(event)
    }
    scrollRequest = null
  }

  override fun getRoot(): TreeNodeViewModel? = root?.viewModel

  override fun getChild(parent: Any?, index: Int): TreeNodeViewModel? = getChildren(parent).getOrNull(index)?.viewModel

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any): Int = getChildren(parent).indexOfFirst { it == child }

  override fun isLeaf(nodeViewModel: Any?): Boolean {
    if (nodeViewModel == null) return true
    nodeViewModel as TreeNodeViewModel
    return nodes[nodeViewModel]?.isLeaf ?: return true
  }

  private fun getChildren(nodeViewModel: Any?): List<Node> {
    if (nodeViewModel == null) return emptyList()
    nodeViewModel as TreeNodeViewModel
    val node = nodes[nodeViewModel] ?: return emptyList()
    node.ensureChildrenAreLoading()
    return node.children ?: emptyList()
  }

  override fun valueForPathChanged(path: TreePath, newValue: Any) {
    throw UnsupportedOperationException("Not an editable model")
  }

  override fun setCachedPresentation(presentation: CachedTreePresentation?) {
    this.cachedPresentation = presentation
    if (root == null && presentation != null) {
      root = createCachedNode(presentation, null, presentation.getRoot())
      treeStructureChanged(root)
    }
  }

  private fun treeStructureChanged(node: Node?) {
    fireEvent(parent = node, nodes = null) {
      treeStructureChanged(it)
    }
  }

  private fun treeNodesRemoved(parent: Node, nodes: Map<Node, Int>) {
    fireEvent(parent, nodes) {
      treeNodesRemoved(it)
    }
  }

  private fun treeNodesInserted(parent: Node, nodes: Map<Node, Int>) {
    fireEvent(parent, nodes) {
      treeNodesInserted(it)
    }
  }

  private fun treeNodesChanged(parent: Node, nodes: Map<Node, Int>?) {
    fireEvent(parent, nodes) {
      treeNodesChanged(it)
    }
  }

  private inline fun fireEvent(parent: Node?, nodes: Map<Node, Int>?, fireEvent: TreeModelListener.(TreeModelEvent) -> Unit) {
    // A special case of nodes == null means that the parent itself has changed.
    if (nodes?.isEmpty() == true || listeners.isEmpty()) return
    val indices = nodes?.values?.toIntArray()
    val values = nodes?.keys?.map { it.viewModel }?.toTypedArray()
    val event = TreeModelEvent(this, parent?.path, indices, values)
    for (listener in listeners) {
      listener.fireEvent(event)
    }
  }

  private inline fun fireSwingTreeModelEvent(fireEvent: TreeSwingModelListener.() -> Unit) {
    for (listener in listeners) {
      if (listener is TreeSwingModelListener) {
        listener.fireEvent()
      }
    }
  }

  private fun addNodeLoadedListener(listener: NodeLoadedListener) {
    nodeLoadedListeners += listener
  }

  private fun removeNodeLoadedListener(listener: NodeLoadedListener) {
    nodeLoadedListeners -= listener
  }

  /**
   * Marks the node as published and notifies the waiting visitors.
   *
   * A node is first created, then its presentation and isLeaf properties are loaded,
   * then the usual tree listeners are notified, and only then the node can be visited.
   * This is because some visitors interact with the tree and its UI,
   * for example, to expand the node being visited,
   * and this is only possible once the UI (which is a regular listener) is notified.
   */
  private fun markPublished(node: Node) {
    if (node.lifecycle == NodeLifecycle.PUBLISHED) return
    if (node.lifecycle != NodeLifecycle.LOADED) {
      LOG.warn(Throwable("Marking the node as published, but it's not loaded: $node"))
      // proceed anyway, as an inconsistent state is still better than a visitor waiting forever
    }
    node.lifecycle = NodeLifecycle.PUBLISHED
    nodeLoadedListeners.forEach { it.nodePublished(node) }
    tryToScroll() // in case when we were asked to scroll to this node before it was published
  }

  override fun accept(visitor: TreeVisitor, allowLoading: Boolean): Promise<TreePath?> {
    val promise = AsyncPromise<TreePath?>()
    val job = treeScope.launch(CoroutineName("Accept $visitor")) {
      try {
        promise.setResult(viewModel.accept(SwingAwareVisitorDelegate(visitor, allowLoading), allowLoading)?.path())
      }
      catch (e: Exception) {
        if (e !is CancellationException) promise.setError(e)
      }
    }
    job.invokeOnCompletion {
      if (promise.isPending) {
        SwingUtilities.invokeLater { promise.cancel() }
      }
    }
    return promise
  }

  private inner class SwingAwareVisitorDelegate(delegate: TreeVisitor, private val allowLoading: Boolean) : TreeViewModelVisitor {
    private val viewModelVisitor = TreeDomainModelDelegatingVisitor(viewModel.domainModel, delegate)

    override suspend fun visit(nodeViewModel: TreeNodeViewModel): TreeVisitor.Action {
      // Before visiting, we must make sure that the node we're about to visit actually exists in this model.
      // For the root node, it means that it was already collected and fully loaded.
      // For child nodes, it means that its parent's children are loaded.
      // In both cases, it's very important that it isn't just created, but actually reported to the tree and its UI!
      // Otherwise, Swing can be in an inconsistent state: the node exists, but, for example, can't be expanded.
      // So here it's very important to wait until it's fully loaded and reported via the listeners.
      // The view model invokes this whole thing under the node view model's scope,
      // so we can be sure we won't wait forever:
      // if the node still exists, it'll make its way here sooner or later,
      // otherwise its scope will be canceled along with our waiting.
      val node = awaitNode(nodeViewModel)
      val result = viewModelVisitor.visit(node.viewModel)
      if (result == TreeVisitor.Action.CONTINUE && allowLoading) {
        // This is needed to ensure that awaitNode() calls for children won't wait forever,
        // because we don't even try to load nodes unless they're requested.
        node.ensureChildrenAreLoading()
      }
      return result
    }

    private suspend fun awaitNode(viewModel: TreeNodeViewModel): Node = suspendCancellableCoroutine { continuation ->
      // This whole thing works strictly on the EDT, so no worries about concurrency issues here.
      // If we check the node, and it's not loaded yet,
      // it's guaranteed that it won't suddenly become loaded before we register the listener.
      // The only possibly non-EDT part here is the invokeOnCancellation() call, but it's just last-chance cleanup.
      val existingNode = nodes[viewModel]
      if (existingNode?.lifecycle == NodeLifecycle.PUBLISHED) {
        continuation.resumeWith(Result.success(existingNode))
        return@suspendCancellableCoroutine
      }
      val listener = object : NodeLoadedListener {
        override fun nodePublished(node: Node) {
          if (node.viewModel == viewModel) {
            removeNodeLoadedListener(this)
            continuation.resumeWith(Result.success(node))
          }
        }
      }
      addNodeLoadedListener(listener)
      continuation.invokeOnCancellation { removeNodeLoadedListener(listener) }
    }

    override fun toString(): String {
      return "SwingAwareVisitorDelegate(allowLoading=$allowLoading, viewModelVisitor=$viewModelVisitor)"
    }
  }

  private fun createCachedNode(treePresentation: CachedTreePresentation, parent: Node?, cachedObject: Any): Node {
    val cachedViewModel = cachedObject as? CachedViewModel ?: CachedViewModel(parent?.viewModel, treePresentation, cachedObject)
    val cachedNode = CachedNode(treePresentation, parent, cachedViewModel)
    nodes[cachedNode.viewModel] = cachedNode
    return cachedNode
  }

  private suspend fun loadNode(parent: RealNode?, viewModel: TreeNodeViewModel): Node? {
    val existingNode = nodes[viewModel]
    if (existingNode != null) {
      LOG.warn(Throwable("The node $viewModel already exists elsewhere as $existingNode"))
      return null
    }
    var result = RealNode(parent, viewModel)
    nodes[viewModel] = result
    return result.awaitLoaded()
  }

  private suspend fun findOrLoadNode(parent: RealNode?, viewModel: TreeNodeViewModel): Node? {
    val existingNode = nodes[viewModel]
    val result = when (existingNode?.lifecycle) {
      NodeLifecycle.CACHED -> {
        LOG.warn(Throwable("Attempt to load a cached $viewModel: $existingNode"))
        null
      }
      // We have to call awaitLoaded() even if it's already loaded, to ensure that the presentation is up to date.
      NodeLifecycle.CREATED, NodeLifecycle.LOADED, NodeLifecycle.PUBLISHED -> existingNode.awaitLoaded()
      NodeLifecycle.DISPOSED -> {
        LOG.warn(Throwable("Attempt to load $viewModel when its node has already been disposed: $existingNode"))
        null
      }
      null -> loadNode(parent, viewModel)
    }
    if (result != null && result.path.parentPath != parent?.path) {
      LOG.warn(Throwable("Attempt to load $viewModel when it has already been registered: $existingNode"))
      return null
    }
    return result
  }

  override fun addTreeModelListener(l: TreeModelListener) {
    listeners.add(0, l) // Swing assumes the reverse order
  }

  override fun removeTreeModelListener(l: TreeModelListener) {
    listeners.remove(l)
  }

  override fun toString(): String {
    return "SwingTreeViewModel@${System.identityHashCode(this)}(viewModel=$viewModel)"
  }

  private inner class RealNode(
    parent: RealNode?,
    override val viewModel: TreeNodeViewModel,
  ) : Node {
    // Must be thread-safe because set by cancellation.
    private val lifecycleReference = AtomicReference(NodeLifecycle.CREATED)

    override var lifecycle: NodeLifecycle
      get() = lifecycleReference.get()
      set(value) = lifecycleReference.set(value)

    override val path: CachingTreePath = parent?.path?.pathByAddingChild(viewModel) as CachingTreePath? ?: CachingTreePath(viewModel)
    val nodeScope: CoroutineScope = (parent?.nodeScope ?: treeScope).childScope(path.toString())

    private var childrenLoadingJob: Job? = null

    override var children: List<Node>? = null

    override var isLeaf: Boolean = true

    init {
      nodeScope.coroutineContext.job.invokeOnCompletion {
        lifecycle = NodeLifecycle.DISPOSED
        nodes.remove(viewModel)
      }
      nodeScope.launch(CoroutineName("Presentation updates of $this")) {
        viewModel.state.collectLatest { nodeState ->
          nodeState as TreeNodeStateImpl
          // Only fire value updates after the node has been published to the UI part of the tree.
          // For two reasons: to avoid unnecessary updates (optimization) and to avoid confusing the UI state.
          if (lifecycle == NodeLifecycle.PUBLISHED) {
            isLeaf = nodeState.presentation.isLeaf
            treeNodesChanged(this@RealNode, null)
          }
        }
      }
    }

    override suspend fun awaitLoaded(): Node? {
      val job = nodeScope.launch {
        isLeaf = (viewModel.state.first() as TreeNodeStateImpl).presentation.isLeaf
      }
      job.join()
      // If the node was canceled, then either this job will be canceled or,
      // if the cancellation happened after the job has completed, the node will be disposed.
      // There's also a slight chance of it being asynchronously canceled right now,
      // but then we care little about it: some code will notice it later and get rid of it.
      // There shouldn't be much async stuff here anyway, as this thing is mostly-EDT,
      // except maybe the case of an external async cancellation.
      return if (job.isCancelled || lifecycle == NodeLifecycle.DISPOSED) {
        null
      }
      else {
        // Possible lifecycle stages:
        // CREATED → it's the first load attempt, mark as loaded;
        // LOADED or VISITABLE → already loaded, do nothing;
        // DISPOSED → handled above, unless it just happened, but then we don't care.
        lifecycleReference.compareAndSet(NodeLifecycle.CREATED, NodeLifecycle.LOADED)
        this
      }
    }

    override fun ensureChildrenAreLoading() {
      if (childrenLoadingJob != null) return
      val cachedChildren = getChildrenFromCachedPresentation()
      if (cachedChildren != null) {
        children = cachedChildren
      }
      else if (this@TreeSwingModelImpl.showLoadingNode) {
        // Need this for clients who expect the "loading..." node to appear immediately,
        // e.g. for com.intellij.ide.projectView.impl.ProjectViewDirectoryExpandDurationMeasurer.
        children = listOf(RealNode(this, LoadingNodeViewModel(viewModel)))
      }
      childrenLoadingJob = nodeScope.launch(CoroutineName("Load children of $this")) {
        viewModel.children.collect { loaded ->
          val children = loaded.map { childViewModel ->
            async(CoroutineName("Load $childViewModel")) {
              findOrLoadNode(this@RealNode, childViewModel)
            }
          }.awaitAll()
          updateChildren(children.filterNotNull().toSet().toList()) // remove duplicates
        }
      }
    }

    private fun getChildrenFromCachedPresentation(): List<Node>? =
      cachedPresentation?.let { cachedPresentation ->
        cachedPresentation.getChildren(viewModel)?.map { child ->
          createCachedNode(cachedPresentation, this, child)
        }
      }

    private fun updateChildren(children: List<Node>) {
      val removedChildren = this.children?.withIndex()?.associateTo(mutableMapOf()) { it.value to it.index } ?: hashMapOf()
      val insertedChildren = children.withIndex().associateTo(mutableMapOf()) { it.value to it.index }
      val changedChildren = removedChildren.keys.intersect(insertedChildren.keys)
        .associateWith { insertedChildren.getValue(it) }
      removedChildren.keys -= changedChildren.keys
      insertedChildren.keys -= changedChildren.keys

      for (removedChild in removedChildren.keys) {
        removedChild.dispose()
      }

      this.children = children

      cachedPresentation?.childrenLoaded(viewModel, children.map { it.viewModel })

      treeNodesRemoved(this, removedChildren)
      treeNodesInserted(this, insertedChildren)
      treeNodesChanged(this, changedChildren)

      for (newChild in insertedChildren.keys) {
        markPublished(newChild)
      }
    }

    override fun dispose() {
      nodeScope.cancel()
    }

    override fun toString(): String {
      return "Node(" +
             "viewModel=$viewModel, " +
             "path=$path, " +
             "isLeaf=$isLeaf, " +
             "lifecycle=$lifecycle, " +
             "${children?.size} children (${if (childrenLoadingJob == null) "not loading" else "loading"})" +
             ")"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Node

      return viewModel == other.viewModel
    }

    override fun hashCode(): Int {
      return viewModel.hashCode()
    }
  }

  private inner class CachedNode(
    treePresentation: CachedTreePresentation,
    parent: Node?,
    override val viewModel: CachedViewModel
  ) : Node {
    override val path: CachingTreePath = parent?.path?.pathByAddingChild(viewModel) as CachingTreePath? ?: CachingTreePath(viewModel)

    override val isLeaf: Boolean
      get() = viewModel.cachedPresentation.isLeaf

    override val children: List<Node>? = viewModel.cachedChildren?.map { child -> createCachedNode(treePresentation, this, child) }

    override var lifecycle: NodeLifecycle = NodeLifecycle.CACHED

    override fun ensureChildrenAreLoading() { }

    override suspend fun awaitLoaded(): Node? = this

    override fun dispose() {
      lifecycle = NodeLifecycle.DISPOSED
      nodes.remove(viewModel)
    }
  }
}

private enum class NodeLifecycle {
  CACHED,
  CREATED,
  LOADED,
  PUBLISHED,
  DISPOSED
}

private sealed interface Node {
  val path: CachingTreePath
  val viewModel: TreeNodeViewModel
  val isLeaf: Boolean
  val children: List<Node>?
  var lifecycle: NodeLifecycle
  fun ensureChildrenAreLoading()
  suspend fun awaitLoaded(): Node?
  fun dispose()
}

// get rid of this when we migrate to suspending visitors
private class TreeDomainModelDelegatingVisitor(
  private val model: TreeDomainModel,
  private val delegate: TreeVisitor,
) : TreeViewModelVisitor {

  override suspend fun visit(node: TreeNodeViewModel): TreeVisitor.Action {
    if (delegate.visitThread() == TreeVisitor.VisitThread.EDT) {
      return actualVisitEdt(delegate, node) // For EDT visiting, the visitor does all three steps in visit().
    }
    else {
      val preVisitResult = preVisit(node, delegate)
      if (preVisitResult != null) return preVisitResult
      val visitResult = actualVisitBgt(delegate, node)
      val postVisitResult = postVisit(visitResult, node, delegate)
      return postVisitResult
    }
  }

  private suspend fun preVisit(node: TreeNodeViewModel, visitor: TreeVisitor): TreeVisitor.Action? =
    (visitor as? EdtBgtTreeVisitor)?.let {
      withContext(Dispatchers.EDT) {
        visitor.preVisitEDT(node.path())
      }
    }

  private suspend fun actualVisitEdt(
    visitor: TreeVisitor,
    node: TreeNodeViewModel,
  ): TreeVisitor.Action =
    withContext(Dispatchers.EDT) {
      visitor.visit(node.path())
    }

  private suspend fun actualVisitBgt(
    visitor: TreeVisitor,
    node: TreeNodeViewModel,
  ): TreeVisitor.Action =
    readAction {
      visitor.visit(node.path())
    }

  private suspend fun postVisit(action: TreeVisitor.Action, node: TreeNodeViewModel, visitor: TreeVisitor): TreeVisitor.Action =
    (visitor as? EdtBgtTreeVisitor)?.let {
      withContext(Dispatchers.EDT) {
        visitor.postVisitEDT(node.path(), action)
      }
    } ?: action

  override fun toString(): String =
    "TreeDomainModelDelegatingVisitor(model=$model, delegate=$delegate)"
}

private class CachedViewModel(
  override val parent: TreeNodeViewModel?,
  treePresentation: CachedTreePresentation,
  private val cachedObject: Any,
) : TreeNodeViewModel {
  val cachedPresentation: TreeNodePresentationImpl = buildCachedPresentation(treePresentation, cachedObject)
  val cachedChildren: List<CachedViewModel>? = treePresentation.getChildren(cachedObject)?.map {
    CachedViewModel(this, treePresentation, it)
  }

  private val stateFlow = MutableStateFlow(TreeNodeStateImpl(
    presentation = cachedPresentation,
    isExpanded = treePresentation.isExpanded(cachedObject)
  ))

  override val state: Flow<TreeNodeState>
    get() = stateFlow

  override val children: Flow<List<TreeNodeViewModel>>
    get() = cachedChildren?.let { flowOf(it) } ?: flowOf(emptyList())

  override fun stateSnapshot(): TreeNodeState = stateFlow.value

  override fun setExpanded(isExpanded: Boolean) {
    stateFlow.value = stateFlow.value.copy(isExpanded = isExpanded)
  }

  override fun getUserObject(): Any = cachedObject

  override fun toString(): String {
    return "CachedViewModel(cachedObject=$cachedObject)"
  }
}

private fun buildCachedPresentation(treePresentation: CachedTreePresentation, cachedObject: Any): TreeNodePresentationImpl {
  val builder = TreeNodePresentationBuilderImpl(treePresentation.isLeaf(cachedObject))
  buildPresentation(builder, cachedObject)
  return builder.build()
}

private fun TreeNodeViewModel.path(): TreePath =
  parent?.path()?.pathByAddingChild(this) ?: CachingTreePath(this)

private class LoadingNodeViewModel(override val parent: TreeNodeViewModel?) : TreeNodeViewModel {
  private val _userObject = LoadingNode()

  private val _state = TreeNodeStateImpl(
    presentation = TreeNodePresentationImpl(
      isLeaf = true,
      icon = null,
      mainText = LoadingNode.getText(),
      fullText = listOf(TreeNodeTextFragment(LoadingNode.getText(), SimpleTextAttributes.GRAY_ATTRIBUTES)),
      toolTip = null,
    ),
    isExpanded = false,
  )

  override val state: Flow<TreeNodeState>
    get() = flowOf(_state)

  override val children: Flow<List<TreeNodeViewModel>>
    get() = flowOf(emptyList())

  override fun stateSnapshot(): TreeNodeState = _state

  override fun setExpanded(isExpanded: Boolean) { }

  override fun getUserObject(): Any = _userObject
}

private val LOG = logger<TreeSwingModelImpl>()
