// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.ui.tree

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.tree.TreePath

internal class TreeViewModelFactoryImpl : TreeViewModelFactory {
  override fun createTreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel =
    TreeViewModelImpl(coroutineScope, domainModel)
}

private class TreeViewModelImpl(private val treeScope: CoroutineScope, override val domainModel: TreeDomainModel) : TreeViewModel {
  private val rootUpdateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val rootViewModelFlow = MutableSharedFlow<TreeNodeViewModelImpl?>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val lastComputedRoot = AtomicReference<TreeNodeViewModelImpl?>()

  override val root: Flow<TreeNodeViewModel?>
    get() = rootViewModelFlow

  private val comparatorRef = AtomicReference<Comparator<in TreeNodeViewModel>?>()

  override var comparator: Comparator<in TreeNodeViewModel>?
    get() = comparatorRef.get()
    set(value) {
      comparatorRef.set(value)
      treeScope.launch(CoroutineName("Invalidating on comparator change for $this")) {
        invalidate(null, true)
      }
    }

  init {
    treeScope.launch(CoroutineName("Root updates for $this")) {
      rootUpdateRequests.collectLatest {
        val rootDomainModel = domainModel.computeRoot()
        if (rootDomainModel == null) {
          lastComputedRoot.set(null)
          rootViewModelFlow.emit(null)
        }
        else {
          val previousModel = lastComputedRoot.get()
          val newLeafState = rootDomainModel.computeLeafState()
          if (previousModel == null || previousModel.domainModel != rootDomainModel || previousModel.leafStateValue != newLeafState) {
            previousModel?.nodeScope?.cancel()
            val newRoot = rootDomainModel.toViewModel(treeScope, newLeafState, comparatorRef)
            lastComputedRoot.set(newRoot)
            rootViewModelFlow.emit(newRoot)
          }
          else {
            previousModel.updatePresentation()
            previousModel.updateChildren()
            rootViewModelFlow.emit(previousModel)
          }
        }
      }
    }
    loadRoot()
  }

  private fun loadRoot() {
    rootViewModelFlow.resetReplayCache()
    check(rootUpdateRequests.tryEmit(Unit))
  }

  override suspend fun invalidate(path: TreePath?, recursive: Boolean) {
    val job = treeScope.launch(CoroutineName("Invalidate $path, recursive=$recursive")) {
      val node = path?.node ?: lastComputedRoot.get()
      node?.invalidate(recursive)
      if (path == null) {
        loadRoot()
      }
    }
    job.join()
  }

  override suspend fun accept(visitor: TreeVisitor, allowLoading: Boolean): TreePath? = acceptImpl(visitor, allowLoading)

  override suspend fun accept(visitor: SuspendingTreeVisitor, allowLoading: Boolean): TreePath? = acceptImpl(visitor, allowLoading)

  private suspend fun <T> acceptImpl(visitor: T, allowLoading: Boolean): TreePath? {
    val root = getFlowValue(rootViewModelFlow, allowLoading)
    if (root == null) return null
    return visit(null, listOf(root), visitor, allowLoading)
  }

  private suspend fun <T> visit(parentPath: TreePath?, nodes: List<TreeNodeViewModelImpl>, visitor: T, allowLoading: Boolean): TreePath? {
    for (node in nodes) {
      val path = parentPath?.pathByAddingChild(node) ?: CachingTreePath(node)
      if (!node.awaitPresentation()) {
        continue // The node was canceled and disappeared.
      }
      val visit = try {
        node.nodeScope.async(CoroutineName("Visiting $node for $visitor")) {
          // Either a visitor is "smart" and knows how to handle contexts,
          if (visitor is SuspendingTreeVisitor) {
            visitor.visit(path)
          }
          // or we have to wrap it into the proper context,
          else if (visitor is TreeVisitor) {
            // which is either EDT
            if (visitor.visitThread() == TreeVisitor.VisitThread.EDT) {
              withContext(Dispatchers.EDT) {
                visitor.visit(path)
              }
            }
            // or whatever the model considers the proper context (usually a read action).
            else {
              domainModel.accessData {
                visitor.visit(path)
              }
            }
          }
          else {
            thisLogger<TreeViewModelImpl>().error(Throwable("Unknown visitor type: $visitor"))
            TreeVisitor.Action.SKIP_SIBLINGS
          }
        }.await()
      }
      catch (_: CancellationException) {
        currentCoroutineContext().ensureActive() // Throw if OUR coroutine was canceled.
        TreeVisitor.Action.SKIP_CHILDREN // Skip the node with its children if ITS scope was canceled.
      }
      when (visit) {
        TreeVisitor.Action.INTERRUPT -> return path
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

  override fun toString(): String {
    return "TreeViewModelImpl@${System.identityHashCode(this)}(domainModel=$domainModel)"
  }
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
    coloredText.add(TreeNodeTextFragmentImpl(text, attributes))
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

  private fun buildColoredText(mainText: String): List<TreeNodeTextFragmentImpl> =
    listOf(TreeNodeTextFragmentImpl(mainText, SimpleTextAttributes.REGULAR_ATTRIBUTES))

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

private class TreeNodeViewModelImpl(
  val nodeScope: CoroutineScope,
  val domainModel: TreeNodeDomainModel,
  val leafStateValue: LeafState,
  val comparator: AtomicReference<Comparator<in TreeNodeViewModel>?>,
) : TreeNodeViewModel {
  private val presentationLoaded = AtomicBoolean()
  private val presentationUpdateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val childrenLoaded = AtomicBoolean()
  private val childrenUpdateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  private val presentationFlow = MutableSharedFlow<TreeNodePresentation>(replay = 1)
  private val lastComputedPresentation = AtomicReference<TreeNodePresentation>()
  val childrenFlow = MutableSharedFlow<List<TreeNodeViewModelImpl>>(replay = 1)
  private val lastComputedChildren = AtomicReference<List<TreeNodeViewModelImpl>>()

  override fun getUserObject(): Any = domainModel.userObject

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

  private fun ensurePresentationIsLoading() {
    if (presentationLoaded.compareAndSet(false, true)) {
      updatePresentation()
    }
  }

  fun ensureChildrenAreLoading() {
    if (childrenLoaded.compareAndSet(false, true)) {
      updateChildren()
    }
  }

  fun invalidate(recursive: Boolean) {
    if (recursive && childrenLoaded.get()) {
      lastComputedChildren.get()?.forEach { it.invalidate(true) }
      childrenFlow.resetReplayCache()
      updateChildren()
    }
    if (presentationLoaded.get()) {
      presentationFlow.resetReplayCache()
      updatePresentation()
    }
  }

  fun updatePresentation() {
    check(presentationUpdateRequests.tryEmit(Unit))
  }

  fun updateChildren() {
    check(childrenUpdateRequests.tryEmit(Unit))
  }

  init {
    // It's a bit complicated.
    // If we know whether the node is a leaf or not, we don't need to compute the children to compute the presentation.
    // If we don't, we need the children, but not their presentations.
    // Computing the children can be expensive, computing their presentations almost certainly is.
    // Therefore, for "unsure if leaf" nodes we maintain a separate flow of "raw" children,
    // which we then use to compute both the presentation and the "real" children (to avoid computing them twice).
    val unpublishedChildrenFlow: MutableSharedFlow<List<TreeNodeDomainModel>>?

    if (leafStateValue != LeafState.ALWAYS && leafStateValue != LeafState.NEVER) {
      unpublishedChildrenFlow = MutableSharedFlow<List<TreeNodeDomainModel>>(replay = 1)
      nodeScope.launch(CoroutineName("Loading children of $this")) {
        merge(childrenUpdateRequests, presentationUpdateRequests).collectLatest {
          unpublishedChildrenFlow.emit(domainModel.computeChildren())
        }
      }
    }
    else {
      unpublishedChildrenFlow = null
    }

    nodeScope.launch(CoroutineName("Value updates of $this")) {
      if (unpublishedChildrenFlow != null) {
        presentationUpdateRequests.combine(unpublishedChildrenFlow) { _, children -> children.isEmpty() }.collectLatest { isLeaf ->
          emitPresentations(isLeaf)
        }
      }
      else {
        presentationUpdateRequests.collectLatest {
          emitPresentations(isLeaf = leafStateValue == LeafState.ALWAYS)
        }
      }
    }

    nodeScope.launch(CoroutineName("Children updates of $this")) {
      if (unpublishedChildrenFlow != null) {
        // This take(1) so this thing is only gets "kick-started" by the first update request,
        // and then it follows unpublishedChildrenFlow, as that flow itself is updated on the same requests.
        // But it can also be updated on presentation update requests, and we don't want to start loading children on those.
        childrenUpdateRequests.take(1).combine(unpublishedChildrenFlow) { _, children -> children }.collectLatest { children ->
          emitChildren(children)
        }
      }
      else {
        childrenUpdateRequests.collectLatest {
          emitChildren(
            if (leafStateValue == LeafState.ALWAYS) {
              emptyList()
            }
            else {
              domainModel.computeChildren()
            }
          )
        }
      }
    }
  }

  private suspend fun emitPresentations(isLeaf: Boolean) {
    val builder = TreeNodePresentationBuilderImpl(isLeaf)
    val lastPresentation = lastComputedPresentation.get()
    // The flow provided by the domain model may cause flickering,
    // as it's supposed to start from "simple" presentations and then add "heavy" parts.
    // To avoid this flickering, we only use all provided presentations on the first load,
    // and then just keep the cached one until the new presentation is computed fully.
    // It's better to have an outdated presentation than flickering.
    if (lastPresentation == null) {
      domainModel.computePresentation(builder).collect { presentation ->
        lastComputedPresentation.set(presentation)
        presentationFlow.emit(presentation)
      }
    }
    else {
      val presentation = domainModel.computePresentation(builder).last()
      lastComputedPresentation.set(presentation)
      presentationFlow.emit(presentation)
    }
  }

  private suspend fun emitChildren(domainChildren: List<TreeNodeDomainModel>) {
    val children = computeChildren(domainChildren)
    lastComputedChildren.set(children)
    childrenFlow.emit(children)
  }

  private suspend fun computeChildren(domainChildren: List<TreeNodeDomainModel>): List<TreeNodeViewModelImpl> {
    val oldChildren = lastComputedChildren.get()?.associateBy { it.domainModel } ?: emptyMap()
    val childViewModels = domainChildren.map { childDomainModel ->
      val newLeafState = childDomainModel.computeLeafState()
      val oldChild = oldChildren[childDomainModel]
      if (oldChild == null || oldChild.leafStateValue != newLeafState) {
        oldChild?.nodeScope?.cancel()
        childDomainModel.toViewModel(nodeScope, newLeafState, comparator)
      }
      else {
        oldChild.apply {
          updatePresentation()
        }
      }
    }
    return sort(childViewModels)
  }

  private suspend fun sort(childViewModels: List<TreeNodeViewModelImpl>): List<TreeNodeViewModelImpl> {
    val comparator = comparator.get()
    if (comparator == null) return childViewModels
    val sorted: MutableList<TreeNodeViewModelImpl> = childViewModels.map { child ->
      nodeScope.async(CoroutineName("Loading to sort: $child")) {
        if (child.awaitPresentation()) child else null
      }
    }.awaitAll().filterNotNull().toMutableList()
    sorted.sortWith(comparator)
    return sorted
  }

  override fun presentationSnapshot(): TreeNodePresentation {
    val presentationSnapshot = lastComputedPresentation.get()
    checkNotNull(presentationSnapshot) { "Presentation has not been computed yet" }
    return presentationSnapshot
  }

  override fun toString(): String {
    return "TreeNodeViewModelImpl@${System.identityHashCode(this)}(" +
           "domainModel=$domainModel, " +
           "leafState=$leafStateValue, " +
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

private fun TreeNodeDomainModel.toViewModel(
  parentScope: CoroutineScope,
  leafState: LeafState,
  comparator: AtomicReference<Comparator<in TreeNodeViewModel>?>,
): TreeNodeViewModelImpl =
  TreeNodeViewModelImpl(parentScope.childScope(toString()), this, leafState, comparator)

private suspend inline fun <T> getFlowValue(flow: MutableSharedFlow<T>, allowLoading: Boolean = false): T? =
  if (allowLoading || flow.replayCache.isNotEmpty()) flow.first() else null

private val TreePath.node: TreeNodeViewModelImpl
  get() = lastPathComponent as TreeNodeViewModelImpl
