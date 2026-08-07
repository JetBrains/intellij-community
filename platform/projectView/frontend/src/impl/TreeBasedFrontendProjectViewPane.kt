// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.SelectInTarget
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomizationUtil
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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionDTO
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.popup.HintUpdateSupply
import com.intellij.ui.stripe.ErrorStripe
import com.intellij.ui.stripe.ErrorStripePainter
import com.intellij.ui.stripe.TreeUpdater
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.asDisposable
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * The UI half of the tree-based frontend Project View pane.
 *
 * It owns the Swing objects (the [tree], its scroll pane, the content panel and the various handlers)
 * and delegates everything else to [paneTreeModel], which owns the data model and the logic that
 * populates it. Keeping the Swing-free logic in [FrontendProjectViewPaneTreeModel] allows testing the
 * whole pipeline (backend models -> events -> populated tree) without instantiating this UI.
 */
internal class TreeBasedFrontendProjectViewPane(
  project: Project,
  descriptor: ProjectViewPaneDescriptorImpl,
) : FrontendProjectViewPane, UiDataProvider {
  private val paneTreeModel = FrontendProjectViewPaneTreeModel(project, descriptor)
  private val tree = Tree(paneTreeModel.treeModel).also {
    it.isRootVisible = false
    CustomizationUtil.installPopupHandler(it, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP)
    TreeUIHelper.getInstance().installTreeSpeedSearch(it)
    HintUpdateSupply.installDataContextHintUpdateSupply(it)
  }
  private val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private val expandRequests = Channel<ExpandRequest>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val treeExpander = ProjectViewTreeExpander(tree, expandRequests)
  private val autoscrollToSourceHandler = MyAutoscrollToSourceHandler(project)
  private val cutCopyPasteDeleteProvider = FrontendProjectViewCutCopyPasteDeleteProvider(paneTreeModel)

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

  override val descriptor: ProjectViewPaneDescriptorImpl
    get() = paneTreeModel.descriptor

  override val displayName: @NlsSafe String
    get() = paneTreeModel.descriptor.presentableName

  override val order: Int
    get() = paneTreeModel.descriptor.order

  override val selectInTargets: Collection<SelectInTarget>
    get() = paneTreeModel.selectInTargets

  override val component: JComponent
    field = ContentPanel(scrollPane)

  override val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
    get() = paneTreeModel.requestChannel

  override var isCurrent: Boolean = false
    set(value) {
      field = value
      paneTreeModel.setCurrent(value)
    }

  init {
    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        val expandedNodeId = (event.path.lastPathComponent as? Node)?.projectViewNode?.id ?: return
        paneTreeModel.requestLoadChildren(expandedNodeId)
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

  override suspend fun manage() {
    coroutineScope {
      launch(CoroutineName("autoscrollToSourceHandler")) {
        autoscrollToSourceHandler.manage()
      }
      launch(CoroutineName("single-click toggle") + Dispatchers.UI) {
        paneTreeModel.getOptionSupport().getActionStateFlow()
          .map { it?.optionStates?.get(ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK)?.isSelected == true }
          .distinctUntilChanged()
          .collectLatest { singleClick ->
            tree.toggleClickCount = if (singleClick) 1 else 2
          }
      }
      launch(CoroutineName("select node requests") + Dispatchers.UI) {
        paneTreeModel.selectionRequests.consumeAsFlow().collectLatest { nodePath ->
          selectNode(nodePath)
        }
      }
      if (Registry.`is`("error.stripe.enabled", defaultValue = true)) {
        launch(CoroutineName("error stripe") + Dispatchers.UI) {
          Disposer.register(asDisposable(), MyTreeUpdater(ErrorStripePainter(true), scrollPane, tree))
          awaitCancellation()
        }
      }
    }
  }

  override fun getOptionSupport(): ProjectViewActionSupport = paneTreeModel.getOptionSupport()

  override suspend fun applyStateChange(event: ProjectViewPaneStateEvent) {
    withContext(Dispatchers.UI) {
      paneTreeModel.applyStateChange(event)
    }
  }

  suspend fun selectNode(nodePath: ProjectViewNodePath) {
    withContext(Dispatchers.UI) {
      LOG.debug { "Resolving $nodePath" }
      val treePath = paneTreeModel.awaitNodePath(nodePath.nodeIds.last())
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
    if (paneTreeModel.treeModel.isLeaf(node)) return
    // For depth == 0 it means that the user explicitly requested to expand this.
    if (depth > 0 && (node as? Node)?.projectViewNode?.isIncludedInExpandAll() == false) {
      LOG.trace { "Won't expand $node because isIncludedInExpandAll == false" }
      return
    }
    result += path
    val childCount = paneTreeModel.treeModel.getChildCount(node)
    for (i in 0 until childCount) {
      val childNode = paneTreeModel.treeModel.getChild(node, i)
      val childPath = path.pathByAddingChild(childNode)
      collectAllExpandableDescendants(childPath, result, depth + 1)
    }
  }

  private suspend fun expandNotLoaded(path: TreePath, depth: Int) {
    // Even with tracing enabled, we don't want to spam messages about the nodes that were already expanded at the fast-path bulk stage.
    val doTraceLogging = LOG.isTraceEnabled && !tree.isExpanded(path) && !paneTreeModel.treeModel.isLeaf(path.lastPathComponent)
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
    if (paneTreeModel.treeModel.isLeaf(node)) {
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
    val children = paneTreeModel.awaitNodeChildren(node) {
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

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ProjectViewPaneId.DATA_KEY] = paneTreeModel.descriptor.id
    sink[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.id
    }
    sink[PlatformDataKeys.CUT_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.COPY_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.PASTE_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = cutCopyPasteDeleteProvider
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = tree.selectionPaths?.mapNotNull { path ->
      (path?.lastPathComponent as? Node)?.projectViewNode?.let { paneTreeModel.createNavigatable(it) }
    }?.toTypedArray()
    sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
  }

  override fun saveStateTo(element: Element) {
    TreeState.createOn(tree, true, false, true).writeExternal(element)
  }

  override fun restoreStateFrom(element: Element) {
    TreeState.createFrom(element).applyTo(tree)
  }
}

private class ProjectViewTreeExpander(tree: Tree, private val expandRequests: SendChannel<ExpandRequest>) : DefaultTreeExpander(tree) {
  override fun isExpandAllVisible(): Boolean {
    return Registry.`is`("ide.project.view.expand.all.action.visible") && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun isExpandAllEnabled(): Boolean {
    return super.isExpandAllEnabled() && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun expandSelected(tree: JTree) {
    val selection = tree.selectionPaths?.toList() ?: return
    val result = expandRequests.trySend(ExpandRequest(selection))
    check(!result.isFailure)
  }

  override fun expandAll(tree: JTree) {
    val root = tree.model?.root ?: return
    val result = expandRequests.trySend(ExpandRequest(listOf(CachingTreePath(root))))
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
