// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.ui.tree

import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

internal class TreeViewModelFactoryImpl : TreeViewModelFactory {
  override fun createTreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel =
    TreeViewModelImpl(coroutineScope, domainModel)
}

private class TreeViewModelImpl(
  private val treeScope: CoroutineScope,
  override val domainModel: TreeDomainModel,
) : TreeViewModel {
  private val updateEpoch = AtomicLong()
  private val updateRequests = MutableSharedFlow<Long>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val completedRequests = MutableSharedFlow<Long>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val pendingUpdates = ConcurrentHashMap<TreeNodeViewModelImpl, NodeUpdate>()
  private val requestedSelection = AtomicReference<Collection<TreeNodeViewModel>>()
  private val selectionFlow = MutableStateFlow(HashSet<TreeNodeViewModelImpl>())
  private val fakeRoot = TreeNodeViewModelImpl(
    parentImpl = null,
    nodeScope = treeScope,
    domainModel = FakeRootDomainModel(domainModel),
    schedule = { update ->
      schedule(update)
    }
  )

  override val root: Flow<TreeNodeViewModel?>
    get() = fakeRoot.children.map { it.firstOrNull() }

  override val selection: StateFlow<Set<TreeNodeViewModel>>
    get() = selectionFlow

  init {
    treeScope.launch(CoroutineName("Updates of ${this@TreeViewModelImpl}")) {
      // No collectLatest here because
      // a) the map is always updated to contain the actual set of updates;
      // b) and, more importantly, we remove the update before starting it,
      // and therefore if we cancel it midway, we won't restart it on the next try.
      // And removing it before starting is necessary so that if someone
      // schedules ANOTHER update of THE SAME node,
      // it's guaranteed that it'll be performed even if the node is being updated right now.
      updateRequests.collect { epoch ->
        while (pendingUpdates.isNotEmpty()) {
          for ((key, value) in pendingUpdates) {
            // First, go up the hierarchy and see if some ancestor needs to update its children.
            // If so, update it first, as its update may cancel its children,
            // and then we'd be wasting CPU cycles here updating them.
            // But since we update the parent first, then subsequent children updates will be no-ops
            // if those children happen to be canceled, because updating happens in the child's coroutine scope.
            val topUpdate = findTopmostRecursiveUpdate(key) ?: (key to value)
            val node = topUpdate.first
            val update = topUpdate.second
            pendingUpdates.remove(node)
            node.update(update)
          }
        }
        val requestedSelection = requestedSelection.getAndSet(null) ?: selectionFlow.value
        val newSelection = HashSet<TreeNodeViewModelImpl>(requestedSelection.size)
        for (nodeToSelect in requestedSelection) {
          if (nodeToSelect is TreeNodeViewModelImpl && nodeToSelect.canSelect()) {
            newSelection.add(nodeToSelect)
          }
        }
        selectionFlow.emit(newSelection)
        check(completedRequests.tryEmit(epoch))
      }
    }
    fakeRoot.setExpanded(true)
  }

  private fun TreeNodeViewModelImpl.schedule(update: NodeUpdate) {
    val existingUpdate = pendingUpdates.remove(this)
    val newUpdate = existingUpdate.merge(update)
    pendingUpdates[this] = newUpdate
    requestUpdate()
  }

  private fun requestUpdate() {
    val newEpoch = updateEpoch.incrementAndGet()
    check(updateRequests.tryEmit(newEpoch))
  }

  private fun findTopmostRecursiveUpdate(node: TreeNodeViewModelImpl): Pair<TreeNodeViewModelImpl, NodeUpdate>? {
    var result: Pair<TreeNodeViewModelImpl, NodeUpdate>? = null
    var node: TreeNodeViewModelImpl? = node
    while (node != null) {
      val update = pendingUpdates[node]
      if (update?.loadChildren == true) {
        result = node to update
      }
      node = node.parentImpl
    }
    return result
  }

  override fun invalidate(node: TreeNodeViewModel?, recursive: Boolean) {
    node as TreeNodeViewModelImpl?
    if (node == null) {
      fakeRoot.invalidate(true)
    }
    else {
      node.invalidate(recursive)
    }
  }

  override fun setSelection(nodes: Collection<TreeNodeViewModel>) {
    requestedSelection.set(nodes)
    requestUpdate()
  }

  override suspend fun awaitUpdates() {
    val currentEpoch = updateEpoch.get()
    treeScope.launch(CoroutineName("Waiting for update $currentEpoch on $this")) {
      completedRequests.collect { epoch ->
        if (epoch >= currentEpoch) {
          cancel()
        }
      }
    }.join()
  }

  override suspend fun accept(visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModel? =
    fakeRoot.visitChildren(visitor, allowLoading)

  override fun toString(): String {
    return "TreeViewModelImpl@${System.identityHashCode(this)}(domainModel=$domainModel)"
  }
}

private data class NodeUpdate(
  val loadPresentation: Boolean = false,
  val loadChildren: Boolean = false,
  val isExpanded: Boolean? = null,
)

private fun NodeUpdate?.merge(other: NodeUpdate): NodeUpdate =
  if (this == null) {
    other
  }
  else {
    NodeUpdate(
      loadPresentation = loadPresentation || other.loadPresentation,
      loadChildren = loadChildren || other.loadChildren,
      isExpanded = other.isExpanded ?: isExpanded,
    )
  }

private class TreeNodeViewModelImpl(
  val parentImpl: TreeNodeViewModelImpl?,
  private val nodeScope: CoroutineScope,
  private val domainModel: TreeNodeDomainModel,
  private val schedule: TreeNodeViewModelImpl.(NodeUpdate) -> Unit,
) : TreeNodeViewModel {
  private val presentationLoaded = AtomicBoolean()
  private val stateFlow = MutableSharedFlow<TreeNodeStateImpl>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val lastComputedState = AtomicReference<TreeNodeStateImpl>()
  private val childrenLoaded = AtomicBoolean()
  private val childrenFlow = MutableSharedFlow<List<TreeNodeViewModelImpl>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val lastComputedChildren = AtomicReference<List<TreeNodeViewModelImpl>>()

  private val isVisible: Boolean
    get() =
      if (parentImpl == null) {
        true
      }
      else {
        parentImpl.isVisible && parentImpl.lastComputedState.get()?.isExpanded == true
      }

  // We use a fake permanent root here to simplify a lot of code.
  // To the outside world, the 2nd level root is visible as the real root.
  override val parent: TreeNodeViewModel?
    get() = if (parentImpl?.parentImpl == null) null else parentImpl

  override val state: Flow<TreeNodeState>
    get() {
      ensurePresentationIsLoading()
      return stateFlow
    }

  override val children: Flow<List<TreeNodeViewModel>>
    get() {
      ensureChildrenAreLoading()
      return childrenFlow
    }

  override fun stateSnapshot(): TreeNodeState {
    val stateSnapshot = lastComputedState.get()
    checkNotNull(stateSnapshot) { "Presentation has not been computed yet" }
    return stateSnapshot
  }

  override fun setExpanded(isExpanded: Boolean) {
    schedule(NodeUpdate(isExpanded = isExpanded))
  }

  override fun getUserObject(): Any = domainModel.getUserObject()

  private fun ensurePresentationIsLoading() {
    if (presentationLoaded.compareAndSet(false, true)) {
      schedule(NodeUpdate(loadPresentation = true, loadChildren = false))
    }
  }

  private fun ensureChildrenAreLoading() {
    if (childrenLoaded.compareAndSet(false, true)) {
      schedule(NodeUpdate(loadPresentation = false, loadChildren = true))
    }
  }

  suspend fun visitChildren(visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModelImpl? {
    if (allowLoading) {
      ensureChildrenAreLoading() // Otherwise getFlowValue() may wait forever.
    }
    val nodes = getFlowValue(childrenFlow, allowLoading) ?: return null
    for (node in nodes) {
      if (!node.awaitState()) {
        continue // The node was canceled and disappeared.
      }
      val visit = try {
        node.nodeScope.async(CoroutineName("Visiting ${node} for $visitor")) {
          visitor.visit(node)
        }.await()
      }
      catch (_: CancellationException) {
        currentCoroutineContext().ensureActive() // Throw if OUR coroutine was canceled.
        TreeVisitor.Action.SKIP_CHILDREN // Skip the node with its children if ITS scope was canceled.
      }
      when (visit) {
        TreeVisitor.Action.INTERRUPT -> return node
        TreeVisitor.Action.CONTINUE -> {
          val result = node.visitChildren(visitor, allowLoading) ?: continue
          return result
        }
        TreeVisitor.Action.SKIP_CHILDREN -> continue
        TreeVisitor.Action.SKIP_SIBLINGS -> break
      }
    }
    return null
  }

  private suspend fun awaitState(): Boolean {
    val result = nodeScope.launch(CoroutineName("Awaiting state of $this")) {
      state.first()
    }
    result.join()
    return !result.isCancelled
  }

  fun invalidate(recursive: Boolean) {
    val reloadChildren = recursive && childrenLoaded.get()
    if (reloadChildren) {
      lastComputedChildren.get()?.forEach { it.invalidate(true) }
      childrenFlow.resetReplayCache()
    }
    val reloadPresentation = presentationLoaded.get()
    if (reloadPresentation) {
      stateFlow.resetReplayCache()
    }
    schedule(NodeUpdate(loadPresentation = reloadPresentation, loadChildren = reloadChildren))
  }

  suspend fun canSelect(): Boolean {
    val checkJob = nodeScope.async(CoroutineName("Checking if $this can be selected")) {
      isVisible
    }
    return try {
      checkJob.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive() // Has the CALLER been canceled?
      false // Cannot select a canceled node.
    }
  }

  suspend fun update(update: NodeUpdate) {
    val updateJob = nodeScope.launch(CoroutineName("Updating $this")) {
      if (update.loadPresentation) {
        reloadState(update.isExpanded)
      }
      if (update.loadChildren) {
        reloadChildren()
      }
      if (!update.loadPresentation && update.isExpanded != null) {
        val lastState = lastComputedState.get()
        if (lastState == null) { // need to load anyway
          reloadState(update.isExpanded)
        }
        else {
          emitState(lastState, lastState.presentation, update.isExpanded)
        }
      }
    }
    updateJob.join()
  }

  private suspend fun reloadState(isExpanded: Boolean?) {
    emitStates(isExpanded)
  }

  private suspend fun reloadChildren() {
    emitChildren(domainModel.computeChildren())
  }

  private suspend fun emitStates(isExpanded: Boolean?) {
    val builder = TreeNodePresentationBuilderImpl(domainModel.computeIsLeaf())
    val lastState = lastComputedState.get()
    // The flow provided by the domain model may cause flickering,
    // as it's supposed to start from "simple" presentations and then add "heavy" parts.
    // To avoid this flickering, we only use all provided presentations on the first load,
    // and then just keep the cached one until the new presentation is computed fully.
    // It's better to have an outdated presentation than flickering.
    if (lastState == null) {
      domainModel.computePresentation(builder).collect { presentation ->
        emitState(lastState, presentation, isExpanded)
      }
    }
    else {
      val presentation = domainModel.computePresentation(builder).last()
      emitState(lastState, presentation, isExpanded)
    }
  }

  private fun emitState(
    lastState: TreeNodeStateImpl?,
    presentation: TreeNodePresentation,
    isExpanded: Boolean?,
  ) {
    val newState = TreeNodeStateImpl(
      presentation = presentation as TreeNodePresentationImpl,
      isExpanded = isExpanded ?: lastState?.isExpanded ?: false,
    )
    lastComputedState.set(newState)
    check(stateFlow.tryEmit(newState))
  }

  private fun emitChildren(domainChildren: List<TreeNodeDomainModel>) {
    val children = computeChildren(domainChildren)
    lastComputedChildren.set(children)
    check(childrenFlow.tryEmit(children))
  }

  private fun computeChildren(domainChildren: List<TreeNodeDomainModel>): List<TreeNodeViewModelImpl> {
    val oldChildren = hashMapOf<TreeNodeDomainModel, TreeNodeViewModelImpl>()
    lastComputedChildren.get()?.associateByTo(oldChildren) { it.domainModel }

    val newChildren = domainChildren.map { childDomainModel ->
      val oldChild = oldChildren.remove(childDomainModel)
      oldChild?.apply { schedule(NodeUpdate(loadPresentation = true, loadChildren = false)) }
      ?: TreeNodeViewModelImpl(this, nodeScope.childScope(childDomainModel.toString()), childDomainModel, schedule)
    }

    oldChildren.values.forEach { it.nodeScope.cancel() }

    return newChildren
  }

  override fun toString(): String {
    return "TreeNodeViewModelImpl@${System.identityHashCode(this)}(" +
           "domainModel=$domainModel, " +
           "presentationLoaded=${presentationLoaded.get()}, " +
           "childrenLoaded=${childrenLoaded.get()}" +
           ")"
  }
}

private suspend inline fun <T> getFlowValue(flow: MutableSharedFlow<T>, allowLoading: Boolean = false): T? =
  if (allowLoading || flow.replayCache.isNotEmpty()) flow.first() else null

private class FakeRootDomainModel(private val treeModel: TreeDomainModel) : TreeNodeDomainModel {
  override suspend fun computeIsLeaf(): Boolean = false

  override suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation> {
    builder.setMainText("Fake root, not visible to the outside world")
    return flowOf(builder.build())
  }

  override suspend fun computeChildren(): List<TreeNodeDomainModel> {
    val realRoot = treeModel.computeRoot()
    return if (realRoot == null) emptyList() else listOf(realRoot)
  }

  override fun getUserObject(): Any = Unit
}

internal class TreeNodePresentationBuilderImpl(val isLeaf: Boolean) : TreeNodePresentationBuilder {
  // these "Value" suffixes to avoid signature clashes with the setters
  private var iconValue: Icon? = null
  private var mainTextValue: String? = null
  private var fullTextValue: MutableList<TreeNodeTextFragment>? = null
  private var toolTipValue: String? = null

  override fun setIcon(icon: Icon?) {
    this.iconValue = icon
  }

  override fun setMainText(text: String) {
    this.mainTextValue = text
  }

  override fun appendTextFragment(text: String, attributes: SimpleTextAttributes) {
    val coloredText = this.fullTextValue ?: mutableListOf()
    coloredText.add(TreeNodeTextFragment(text, attributes))
    this.fullTextValue = coloredText
  }

  override fun setToolTipText(toolTip: String?) {
    this.toolTipValue = toolTip
  }

  override fun build(): TreeNodePresentationImpl {
    val specifiedMainText = this.mainTextValue
    val specifiedFullText = this.fullTextValue
    val mainText: String
    val fullText: List<TreeNodeTextFragment>
    if (specifiedMainText != null) {
      if (specifiedFullText != null) {
        mainText = specifiedMainText
        fullText = specifiedFullText
      }
      else {
        mainText = specifiedMainText
        fullText = buildColoredText(mainText)
      }
    }
    else {
      if (specifiedFullText != null) {
        mainText = buildMainText(specifiedFullText)
        fullText = specifiedFullText
      }
      else {
        throw IllegalStateException("Either the main text or the full text must be specified")
      }
    }
    return TreeNodePresentationImpl(
      isLeaf = isLeaf,
      icon = iconValue,
      mainText = mainText,
      fullText = fullText,
      toolTip = toolTipValue,
    )
  }

  private fun buildColoredText(mainText: String): List<TreeNodeTextFragment> =
    listOf(TreeNodeTextFragment(mainText, SimpleTextAttributes.REGULAR_ATTRIBUTES))

  private fun buildMainText(fullText: List<TreeNodeTextFragment>): String {
    val builder = StringBuilder()
    for (fragment in fullText) {
      val attributes = fragment.attributes
      if (attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor) break
      builder.append(fragment.text)
    }
    return builder.toString()
  }
}
