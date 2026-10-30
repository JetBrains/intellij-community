// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.IdeBundle
import com.intellij.ide.SelectInTarget
import com.intellij.ide.actions.OpenInRightSplitActionProvider
import com.intellij.ide.projectView.impl.ProjectViewInitNotifier
import com.intellij.ide.util.treeView.CachedTreePresentationData
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.RestorePaneStateOptions
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.runSafelyCancellable
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionDTO
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.stripe.ErrorStripe
import com.intellij.ui.stripe.ErrorStripePainter
import com.intellij.ui.stripe.TreeUpdater
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeStateListener
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.asDisposable
import com.intellij.util.ui.initOnShow
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import java.util.function.BiFunction
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
@Suppress("ConvertToExplicitBackingFields") // no, thank you, I don't want to write `componentToFocus` everywhere
internal class TreeBasedFrontendProjectViewPane(
  private val project: Project,
  descriptor: ProjectViewPaneDescriptorImpl,
) : FrontendProjectViewPane, UiDataProvider {
  private val paneTreeModel = FrontendProjectViewPaneTreeModel(project, descriptor)
  private val tree = FrontendProjectViewTree(paneTreeModel.treeModel)
  private val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private val expandRequests = MutableSharedFlow<ExpandRequest>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val treeExpander = ProjectViewTreeExpander(tree, expandRequests)
  private val autoscrollToSourceHandler = MyAutoscrollToSourceHandler(project)
  private val cutCopyPasteDeleteProvider = FrontendProjectViewCutCopyPasteDeleteProvider(paneTreeModel)
  private val lateTopLevelCachedPresentation = LateTopLevelCachedPresentation(tree, paneTreeModel.treeModel)
  private val singleRootExpandRequest = MutableStateFlow(SingleRootExpand.MAYBE_NEEDED)
  private enum class SingleRootExpand {
    MAYBE_NEEDED,
    DEFINITELY_NEEDED,
    NOT_NEEDED,
  }

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

  val displayName: @NlsSafe String
    get() = paneTreeModel.descriptor.presentableName

  override val selectInTargets: Collection<SelectInTarget>
    get() = paneTreeModel.selectInTargets

  override val component: JComponent
    field = ContentPanel(scrollPane)

  override val componentToFocus: JComponent
    get() = tree

  val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
    get() = paneTreeModel.requestChannel

  override var isCurrent: Boolean = false
    set(value) {
      field = value
      paneTreeModel.setCurrent(value)
    }

  init {
    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        // Something was expanded, no matter what and why, the "first" expand no longer needed.
        singleRootExpandRequest.value = SingleRootExpand.NOT_NEEDED
        val expandedNodeId = (event.path.lastPathComponent as? Node)?.projectViewNode?.id ?: return
        paneTreeModel.requestLoadChildren(expandedNodeId)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) { }
    })
    tree.addTreeExpansionListener(TreeStateInitListener(project, tree))
    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)
    autoscrollToSourceHandler.install(tree)
    tree.launchOnShow("expand requests") {
      expandRequests.collectLatest { expandRequest ->
        expand(expandRequest)
      }
    }
    tree.initOnShow("first expand and selection") {
      val firstTopNodes = combine(paneTreeModel.rootChildren, singleRootExpandRequest) { children, expand ->
        if (children.isEmpty()) {
          LOG.debug { "Pane $id got empty top children, waiting for more..." }
          null // not ready yet
        }
        else when (expand) {
          SingleRootExpand.MAYBE_NEEDED -> {
            // Continue waiting until either:
            // - an empty state is restored (=> expand needed) or
            // - something is expanded (=> expand no longer needed).
            LOG.debug { "Pane $id got non-empty top children, but it's not clear yet whether we need to expand the first node" }
            null
          }
          SingleRootExpand.DEFINITELY_NEEDED, SingleRootExpand.NOT_NEEDED -> {
            // In both cases we need to stop.
            LOG.debug { "Pane $id got non-empty top children, stopping to figure out whether we need to expand the first node" }
            children
          }
        }
      }.filterNotNull().first()
      LOG.debug { "The first top nodes of the pane $id are: $firstTopNodes" }
      if (singleRootExpandRequest.value == SingleRootExpand.DEFINITELY_NEEDED) {
        val singleExpandableNode = firstTopNodes.singleOrNull { nodePath ->
          (TreeUtil.getLastUserObject(nodePath) as? ProjectViewNodeModelImpl<*>)?.shouldBeInitiallyExpanded() == true
        }
        if (singleExpandableNode != null) {
          LOG.debug { "The pane $id has a single top expandable node, expanding and selecting" }
          tree.selectionPath = singleExpandableNode
          tree.expandPath(singleExpandableNode)
        }
        else {
          LOG.debug { "The pane $id doesn't have a single top expandable node, not expanding" }
        }
      }
      else {
        LOG.debug { "The pane $id doesn't need to expand the first top node (already expanded something)" }
      }
    }
    val restoreSelectionListener = RestoreSelectionListener()
    ClientProperty.put(tree, RestoreSelectionListener.MATCHER, BiFunction { pathComponent, thisComponent ->
      if (pathComponent == thisComponent) {
        true
      }
      else {
        val pathId = TreeState.calcId(pathComponent)
        val pathType = TreeState.calcType(pathComponent)
        val thisId = TreeState.calcId(thisComponent)
        val thisType = TreeState.calcType(thisComponent)
        pathId == thisId && pathType == thisType
      }
    })
    tree.addTreeSelectionListener(restoreSelectionListener)
    enableDnD(tree, paneTreeModel)
    if (!descriptor.isDefault) {
      tree.emptyText
        .setText(IdeBundle.message("scope.view.empty.text"))
        .appendSecondaryText(IdeBundle.message("scope.view.empty.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
          if (!project.isDisposed) {
            ProjectViewToolWindowServiceImpl.getInstance(project).selectDefaultPane()
          }
        }
    }
  }

  override suspend fun manage() {
    coroutineScope {
      launch(CoroutineName("paneTreeModel management for the pane $id")) {
        runSafelyCancellable(LOG, taskDescription = { "paneTreeModel management for the pane $id" }) {
          paneTreeModel.manage()
        }
      }
      launch(CoroutineName("single-click toggle for the pane $id") + Dispatchers.UI) {
        paneTreeModel.getOptionSupport().getActionStateFlow()
          .map { it?.optionStates?.get(ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK)?.isSelected == true }
          .distinctUntilChanged()
          .collectLatest { singleClick ->
            tree.toggleClickCount = if (singleClick) 1 else 2
          }
      }
      launch(CoroutineName("select node requests for the pane $id") + Dispatchers.UI) {
        paneTreeModel.selectionRequests.consumeAsFlow().collectLatest { request ->
          runSafelyCancellable(LOG, taskDescription = { "select node requests for the pane $id" }) {
            selectNode(request.nodePath)
            if (request.requestFocus) {
              IdeFocusManager.getGlobalInstance().requestFocusInProject(tree, project)
            }
          }
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

  fun getOptionSupport(): ProjectViewActionSupport = paneTreeModel.getOptionSupport()

  suspend fun applyStateChange(event: ProjectViewPaneStateEvent) {
    withContext(Dispatchers.UI) {
      paneTreeModel.applyStateChange(event)
      // Normally a newly loaded node can't be expanded.
      // But it's possible when the pane was fully unloaded, and then reloaded again.
      // Then, the frontend pane can retain some stale state, and in that state some nodes can be pre-expanded.
      // We need to load every visible node, otherwise we may end with broken frontend-only nodes that can't do anything (IJPL-254925).
      if (event is ProjectViewChildrenLoaded) {
        loadVisibleGrandchildren(event.parentId)
      }
      if (event is ProjectViewChildrenLoaded || event is ProjectViewNodeAdded) {
        lateTopLevelCachedPresentation.topLevelNodesMayHaveChanged()
      }
    }
  }

  private fun loadVisibleGrandchildren(parentId: Long) {
    val parent = paneTreeModel.getTreePathById(parentId)?.lastPathComponent as? Node? ?: return
    val children = parent.children().asSequence().filterIsInstance<Node>().toList()
    for (child in children) {
      if (tree.isExpanded(child.treePath)) {
        LOG.debug { "The newly loaded node ${child.id} was previously expanded, requesting its children..." }
        paneTreeModel.requestLoadChildren(child.id)
      }
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
    runSafelyCancellable(LOG, taskDescription = { "expand request for the pane $id: $expandRequest" }) {
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
      finally {
        tree.resumeExpandCollapseAccessibilityAnnouncements()
      }
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
    sink[ProjectViewPaneKind.DATA_KEY] = paneTreeModel.descriptor.kind
    sink[PlatformDataKeys.CUT_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.COPY_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.PASTE_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = cutCopyPasteDeleteProvider
    sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
    val selectionPaths = tree.selectionPaths ?: return
    uiDataSnapshotForSelection(sink, selectionPaths)
  }

  private fun uiDataSnapshotForSelection(
    sink: DataSink,
    selectionPaths: Array<out TreePath>,
  ) {
    val selectedNodes = selectionPaths.mapNotNull { path ->
      path.lastPathComponent as? Node
    }
    sink[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] = selectedNodes.map { node ->
      node.projectViewNode.id
    }
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = selectedNodes.map { node ->
      node.projectViewNode.let { paneTreeModel.createNavigatable(it) }
    }.toTypedArray()
    sink.lazy(CommonDataKeys.VIRTUAL_FILE) {
      selectedNodes.firstOrNull()?.projectViewNode?.userObject?.getVirtualFile() 
    }
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY) {
      selectedNodes.mapNotNull { it.projectViewNode.userObject.getVirtualFile() }.toTypedArray()
    }
    val selectedFileNodes = selectedNodes.map { it.projectViewNode }.filter { !it.isDirectory() && it.canNavigateToSource() }
    if (selectedFileNodes.isNotEmpty()) {
      sink[OpenInRightSplitActionProvider.DATA_KEY] = object : OpenInRightSplitActionProvider {
        override fun canOpenInRightSplit(e: AnActionEvent): Boolean = true

        override fun openInRightSplit(e: AnActionEvent): Boolean {
          paneTreeModel.openInRightSplit(selectedFileNodes)
          return true
        }
      }
    }
  }

  override fun saveStateTo(element: Element): Boolean {
    if (paneTreeModel.isEffectivelyEmpty) return false
    TreeState.createOn(tree, true, true, true).writeExternal(element)
    return true
  }

  override fun restoreStateFrom(element: Element?, options: RestorePaneStateOptions) {
    val treeState = TreeState.createFrom(element)
    if (!treeState.isEmpty) {
      singleRootExpandRequest.value = SingleRootExpand.NOT_NEEDED
      treeState.applyTo(tree, options.restoreSelection)
      lateTopLevelCachedPresentation.stateRestored(treeState.cachedPresentationData)
    }
    else {
      singleRootExpandRequest.value = SingleRootExpand.DEFINITELY_NEEDED
      lateTopLevelCachedPresentation.stateRestored(null)
    }
    if (tree.selectionPath == null && tree.rowCount > 0) {
      LOG.debug { "The pane $id has no initial selection, selecting the first row" }
      tree.selectionPath = tree.getPathForRow(0)
    }
  }
}

private class ProjectViewTreeExpander(tree: Tree, private val expandRequests: MutableSharedFlow<ExpandRequest>) : DefaultTreeExpander(tree) {
  override fun isExpandAllVisible(): Boolean {
    return Registry.`is`("ide.project.view.expand.all.action.visible") && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun isExpandAllEnabled(): Boolean {
    return super.isExpandAllEnabled() && !Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively")
  }

  override fun expandSelected(tree: JTree) {
    val selection = tree.selectionPaths?.toList() ?: return
    val result = expandRequests.tryEmit(ExpandRequest(selection))
    check(result)
  }

  override fun expandAll(tree: JTree) {
    val root = tree.model?.root ?: return
    val result = expandRequests.tryEmit(ExpandRequest(listOf(CachingTreePath(root))))
    check(result)
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

private class TreeStateInitListener(
  private val project: Project,
  private val tree: Tree,
) : TreeStateListener {
  override fun treeStateRestoreStarted(event: TreeExpansionEvent) {
    // reported earlier, when the TW only begins to load
  }

  override fun treeStateCachedStateRestored(event: TreeExpansionEvent) {
    ProjectViewInitNotifier.getInstance(project).initCachedNodesLoaded()
  }

  override fun treeStateRestoreFinished(event: TreeExpansionEvent) {
    ProjectViewInitNotifier.getInstance(project).initCompleted()
    tree.removeTreeExpansionListener(this)
  }

  override fun treeExpanded(event: TreeExpansionEvent?) { }

  override fun treeCollapsed(event: TreeExpansionEvent?) { }
}

/**
 * Applies the restored cached presentation to the top-level nodes that are loaded late.
 *
 * The children of the root can load in batches.
 * While the restored presentation is pending, the model applies it to a late node itself.
 * After the presentation is done, this class applies the entries for the late nodes once more.
 * Each top-level entry applies at most once.
 */
private class LateTopLevelCachedPresentation(
  private val tree: Tree,
  private val treeModel: DefaultTreeModelWithCachedPresentation,
) {
  private var cachedRoot: CachedTreePresentationData? = null
  private var unmatchedEntries: List<CachedTreePresentationData> = emptyList()

  fun stateRestored(cachedRoot: CachedTreePresentationData?) {
    this.cachedRoot = cachedRoot
    unmatchedEntries = cachedRoot?.children.orEmpty()
    // The restored presentation has already applied to the loaded top-level nodes.
    matchEntries()
  }

  fun topLevelNodesMayHaveChanged() {
    val cachedRoot = cachedRoot ?: return
    val entries = unmatchedEntries
    if (!matchEntries()) return
    if (tree.cachedPresentation != null) {
      LOG.debug("The restored cached presentation is pending, so it applies to the late top-level nodes itself")
      return
    }
    LOG.debug("Applying the cached presentation to the late top-level nodes")
    // The entries that are still unmatched stay in the presentation, so they apply to the nodes that come while it's pending.
    val presentation = CachedTreePresentationData(cachedRoot.pathElement, cachedRoot.presentation, cachedRoot.extraAttributes, entries, cachedRoot.isExpanded)
    tree.cachedPresentation = presentation.createTree()
    treeModel.promiseRealNodes().onProcessed {
      tree.cachedPresentation = null
    }
  }

  /**
   * Forgets the entries that match a real top-level node.
   *
   * @return `true` if any entry matched
   */
  private fun matchEntries(): Boolean {
    val topLevelNodes = treeModel.root?.children()?.toList()?.filterIsInstance<Node>().orEmpty()
    val stillUnmatched = unmatchedEntries.filter { entry -> topLevelNodes.none { entry.pathElement.matches(it) } }
    val anyMatched = stillUnmatched.size < unmatchedEntries.size
    unmatchedEntries = stillUnmatched
    return anyMatched
  }
}

private val LOG = logger<TreeBasedFrontendProjectViewPane>()
