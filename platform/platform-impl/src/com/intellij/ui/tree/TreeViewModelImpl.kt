// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.ui.tree

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
          if (previousModel == null || previousModel.domainModel != rootDomainModel) {
            previousModel?.nodeScope?.cancel()
            val newRoot = TreeNodeViewModelImpl(
              parent = null,
              nodeScope = treeScope.childScope(rootDomainModel.toString()),
              domainModel = rootDomainModel,
            )
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

  override suspend fun invalidate(node: TreeNodeViewModel?, recursive: Boolean) {
    node as TreeNodeViewModelImpl?
    val job = treeScope.launch(CoroutineName("Invalidate $node, recursive=$recursive")) {
      val node = node ?: lastComputedRoot.get()
      node?.invalidate(recursive)
      if (node == null) {
        loadRoot()
      }
    }
    job.join()
  }

  override suspend fun accept(visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModel? {
    val root = getFlowValue(rootViewModelFlow, allowLoading)
    if (root == null) return null
    return visit(null, listOf(root), visitor, allowLoading)
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

  override fun toString(): String {
    return "TreeViewModelImpl@${System.identityHashCode(this)}(domainModel=$domainModel)"
  }
}

private class TreeNodeViewModelImpl(
  override val parent: TreeNodeViewModel?,
  val nodeScope: CoroutineScope,
  val domainModel: TreeNodeDomainModel,
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
    nodeScope.launch(CoroutineName("Presentation updates of $this")) {
      presentationUpdateRequests.collectLatest {
        emitPresentations()
      }
    }
    nodeScope.launch(CoroutineName("Children updates of $this")) {
      childrenUpdateRequests.collectLatest {
        emitChildren(domainModel.computeChildren())
      }
    }
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

  private fun computeChildren(domainChildren: List<TreeNodeDomainModel>): List<TreeNodeViewModelImpl> {
    val oldChildren = lastComputedChildren.get()?.associateBy { it.domainModel } ?: emptyMap()
    return domainChildren.map { childDomainModel ->
      val oldChild = oldChildren[childDomainModel]
      oldChild?.apply { updatePresentation() }
      ?: TreeNodeViewModelImpl(this, nodeScope.childScope(childDomainModel.toString()), childDomainModel)
    }
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

private suspend inline fun <T> getFlowValue(flow: MutableSharedFlow<T>, allowLoading: Boolean = false): T? =
  if (allowLoading || flow.replayCache.isNotEmpty()) flow.first() else null

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
