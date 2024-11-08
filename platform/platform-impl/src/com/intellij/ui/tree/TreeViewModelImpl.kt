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
import javax.swing.tree.TreePath

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
  private val fakeRoot = TreeNodeViewModelImpl(null, treeScope, FakeRootDomainModel(domainModel))

  override val root: Flow<TreeNodeViewModel?>
    get() = fakeRoot.children.map { it.firstOrNull() }

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
            update.update(node)
          }
        }
        check(completedRequests.tryEmit(epoch))
      }
    }
    scheduleNodeUpdate(fakeRoot, loadPresentation = true, loadChildren = true)
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

  private fun scheduleNodeUpdate(node: TreeNodeViewModelImpl, loadPresentation: Boolean, loadChildren: Boolean) {
    val existingUpdate = pendingUpdates.remove(node)
    val newUpdate = existingUpdate.merge(NodeUpdate(loadPresentation, loadChildren))
    pendingUpdates[node] = newUpdate
    val newEpoch = updateEpoch.incrementAndGet()
    check(updateRequests.tryEmit(newEpoch))
  }

  override suspend fun accept(visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModel? {
    val root = getFlowValue(fakeRoot.childrenFlow, allowLoading)
    if (root == null) return null
    return visit(null, root, visitor, allowLoading)
  }

  private suspend fun visit(parentPath: TreePath?, nodes: List<TreeNodeViewModelImpl>, visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModel? {
    for (node in nodes) {
      val path = parentPath?.pathByAddingChild(node) ?: CachingTreePath(node)
      if (!node.awaitPresentation()) {
        continue // The node was canceled and disappeared.
      }
      val visit = try {
        node.nodeScope.async(CoroutineName("Visiting $node for $visitor")) {
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
          if (allowLoading) {
            node.ensureChildrenAreLoading() // Otherwise getFlowValue() may wait forever.
          }
          val children = getFlowValue(node.childrenFlow, allowLoading) ?: continue
          val result = visit(path, children, visitor, allowLoading) ?: continue
          return result
        }
        TreeVisitor.Action.SKIP_CHILDREN -> continue
        TreeVisitor.Action.SKIP_SIBLINGS -> break
      }
    }
    return null
  }

  private data class NodeUpdate(
    val loadPresentation: Boolean,
    val loadChildren: Boolean,
  ) {
    suspend fun update(node: TreeNodeViewModelImpl) {
      val updateJob = node.nodeScope.launch(CoroutineName("Updating $node")) {
        if (loadPresentation) {
          node.reloadPresentation()
        }
        if (loadChildren) {
          node.reloadChildren()
        }
      }
      updateJob.join()
    }
  }

  private fun NodeUpdate?.merge(other: NodeUpdate): NodeUpdate =
    if (this == null) {
      other
    }
    else {
      NodeUpdate(
        loadPresentation = loadPresentation || other.loadPresentation,
        loadChildren = loadChildren || other.loadChildren,
      )
    }

  override fun toString(): String {
    return "TreeViewModelImpl@${System.identityHashCode(this)}(domainModel=$domainModel)"
  }

  private inner class TreeNodeViewModelImpl(
    val parentImpl: TreeNodeViewModelImpl?,
    val nodeScope: CoroutineScope,
    val domainModel: TreeNodeDomainModel,
  ) : TreeNodeViewModel {
    private val presentationLoaded = AtomicBoolean()
    private val presentationFlow = MutableSharedFlow<TreeNodePresentation>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lastComputedPresentation = AtomicReference<TreeNodePresentation>()
    private val childrenLoaded = AtomicBoolean()
    val childrenFlow = MutableSharedFlow<List<TreeNodeViewModelImpl>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lastComputedChildren = AtomicReference<List<TreeNodeViewModelImpl>>()

    // We use a fake permanent root here to simplify a lot of code.
    // To the outside world, the 2nd level root is visible as the real root.
    override val parent: TreeNodeViewModel?
      get() = if (parentImpl?.parentImpl == null) null else parentImpl

    override val presentation: Flow<TreeNodePresentation>
      get() {
        ensurePresentationIsLoading()
        return presentationFlow
      }

    override val children: Flow<List<TreeNodeViewModel>>
      get() {
        ensureChildrenAreLoading()
        return childrenFlow
      }

    override fun getUserObject(): Any = domainModel.getUserObject()

    private fun ensurePresentationIsLoading() {
      if (presentationLoaded.compareAndSet(false, true)) {
        scheduleNodeUpdate(this, loadPresentation = true, loadChildren = false)
      }
    }

    fun ensureChildrenAreLoading() {
      if (childrenLoaded.compareAndSet(false, true)) {
        scheduleNodeUpdate(this, loadPresentation = false, loadChildren = true)
      }
    }

    fun invalidate(recursive: Boolean) {
      val reloadChildren = recursive && childrenLoaded.get()
      if (reloadChildren) {
        lastComputedChildren.get()?.forEach { it.invalidate(true) }
        childrenFlow.resetReplayCache()
      }
      val reloadPresentation = presentationLoaded.get()
      if (reloadPresentation) {
        presentationFlow.resetReplayCache()
      }
      scheduleNodeUpdate(this, loadPresentation = reloadPresentation, loadChildren = reloadChildren)
    }

    suspend fun reloadPresentation() {
      emitPresentations()
    }

    suspend fun reloadChildren() {
      emitChildren(domainModel.computeChildren())
    }

    private suspend fun emitPresentations() {
      val builder = TreeNodePresentationBuilderImpl(domainModel.computeIsLeaf())
      val lastPresentation = lastComputedPresentation.get()
      // The flow provided by the domain model may cause flickering,
      // as it's supposed to start from "simple" presentations and then add "heavy" parts.
      // To avoid this flickering, we only use all provided presentations on the first load,
      // and then just keep the cached one until the new presentation is computed fully.
      // It's better to have an outdated presentation than flickering.
      if (lastPresentation == null) {
        domainModel.computePresentation(builder).collect { presentation ->
          lastComputedPresentation.set(presentation)
          check(presentationFlow.tryEmit(presentation))
        }
      }
      else {
        val presentation = domainModel.computePresentation(builder).last()
        lastComputedPresentation.set(presentation)
        check(presentationFlow.tryEmit(presentation))
      }
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
        oldChild?.apply { scheduleNodeUpdate(oldChild, loadPresentation = true, loadChildren = false) }
        ?: TreeNodeViewModelImpl(this, nodeScope.childScope(childDomainModel.toString()), childDomainModel)
      }

      oldChildren.values.forEach { it.nodeScope.cancel() }

      return newChildren
    }

    override fun presentationSnapshot(): TreeNodePresentation {
      val presentationSnapshot = lastComputedPresentation.get()
      checkNotNull(presentationSnapshot) { "Presentation has not been computed yet" }
      return presentationSnapshot
    }

    override fun toString(): String {
      return "TreeNodeViewModelImpl@${System.identityHashCode(this)}(" +
             "domainModel=$domainModel, " +
             "presentationLoaded=${presentationLoaded.get()}, " +
             "childrenLoaded=${childrenLoaded.get()}" +
             ")"
    }

    suspend fun awaitPresentation(): Boolean {
      val result = nodeScope.launch {
        presentation.first()
      }
      result.join()
      return !result.isCancelled
    }
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

  override fun build(): TreeNodePresentation {
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
