// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.impl

import com.intellij.ide.IdeView
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.projectView.HelpID
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.BackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SelectInRequest
import com.intellij.platform.projectView.pane.SuspendingBackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilder
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.platform.projectView.settings.allProjectViewPaneOptions
import com.intellij.platform.projectView.settings.allProjectViewPaneSortKeys
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.containers.nullize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Experimental
interface ProjectViewTreeNodeProvider<T> {
  suspend fun getChildren(parent: T?): List<T>?
  suspend fun createNodeModel(id: Long, node: T): BackendProjectViewNodeModel<T>?
}

@ApiStatus.Experimental
abstract class TreeBasedProjectViewPaneModel<T>(protected val project: Project) : ProjectViewPaneModel {
  private val currentTreeState = AtomicReference<ProjectViewPaneTreeState?>(null)

  val suspendingState: SuspendingBackendProjectViewPaneStateAccessor<T>?
    get() = currentTreeState.load()?.suspendingState
  val state: BackendProjectViewPaneStateAccessor<T>?
    get() = currentTreeState.load()?.state

  protected abstract val psi: ProjectViewPsiExtractor<T>

  protected open suspend fun isDefault(): Boolean = false

  protected abstract suspend fun id(): ProjectViewPaneId
  protected abstract suspend fun presentableName(): @NlsSafe String
  protected abstract suspend fun order(): Int

  override suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor {
    builder.setDefault(isDefault())
    return builder.build(id(), presentableName(), order())
  }

  protected abstract suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewTreeNodeProvider<T>

  protected abstract suspend fun createUpdater(): ProjectViewUpdater

  protected abstract fun createSelectNodeVisitorProvider(): ProjectViewSelectNodeVisitorProvider<T>

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    val treeState = ProjectViewPaneTreeState(id(), builder, createNodeProvider(builder.asSettingsAccessor()))
    currentTreeState.store(treeState)
    try {
      treeState.run()
    }
    finally {
      currentTreeState.store(null)
    }
  }

  /**
   * Suspends until every update that was submitted before this call has been applied to the tree.
   *
   * "Submitted" means either already passed to [updateNode], or already sitting in the updater's
   * internal queue (see [ProjectViewUpdaterProgressReporter]). This lets a caller mutate the VFS/PSI
   * and then be sure, once this returns, that the changes are reflected in the tree state.
   *
   * Throws [CancellationException] if the pane is not being managed, or if its management finishes
   * before the pending updates are applied.
   */
  suspend fun awaitPendingUpdates() {
    val treeState = currentTreeState.load()
                    ?: throw CancellationException("The Project View pane is not being managed")
    treeState.awaitPendingUpdates()
  }

  protected fun updateSettings() {
    currentTreeState.load()?.scheduleUpdateSettings()
  }

  @ApiStatus.OverrideOnly
  protected open suspend fun onStateChanged(state: SuspendingBackendProjectViewPaneStateAccessor<T>) { }

  private fun buildSettingsState(settingsStateBuilder: ProjectViewPaneSettingsStateBuilder) {
    val settingsService = ProjectViewPaneSettingsService.getInstance(project)
    for (option in allProjectViewPaneOptions()) {
      settingsStateBuilder.setOptionState(
        option = option,
        isSelected = settingsService.isOptionSelected(option),
        isEnabled = settingsService.isOptionEnabled(option) && supportsOption(option),
        isAlwaysVisible = settingsService.isOptionAlwaysVisible(option),
      )
    }
    settingsStateBuilder.setAvailableSortKeys(supportedSortKeys())
    settingsStateBuilder.setSortKey(settingsService.getSortKey())
    val fileNesting = settingsService.getFileNesting()
    settingsStateBuilder.setFileNesting(
      fileNesting.isFileNestingOn && supportsFileNesting(),
      supportsFileNesting(),
      fileNesting.nestingRules,
      ProjectViewFileNestingService.getInstance().getDefaultRules(),
    )
  }

  protected open fun supportsOption(option: ProjectViewPaneOption): Boolean {
    return true
  }

  protected open fun supportedSortKeys(): List<ProjectViewPaneSortKey> {
    return allProjectViewPaneSortKeys()
  }

  protected open fun supportsFileNesting(): Boolean {
    return false
  }

  override suspend fun setPaneSelected(
    isSelected: Boolean,
    options: ProjectViewPaneSelectionOptions,
  ) {
  }

  override suspend fun loadChildren(
    parentId: Long,
    options: ProjectViewPaneLoadChildrenOptions,
  ) {
    currentTreeState.load()?.scheduleLoadChildren(parentId)
  }

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
    currentTreeState.load()?.scheduleSetOption(option, newValue)
  }

  override suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey) {
  }

  override suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue) {
    ProjectViewFileNestingService.getInstance().setRules(fileNestingValue.nestingRules)
  }

  suspend fun visitTree(visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action): BackendProjectViewNodeModel<T>? {
    return currentTreeState.load()?.visitTree(visitNode)
  }

  fun updateNode(nodeId: Long, options: ((ProjectViewNodeUpdateOptionsBuilder) -> Unit)? = null) {
    currentTreeState.load()?.updateNode(nodeId, options)
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val selectedIds = snapshot[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] ?: emptyList()
    val selectedNodes = selectedIds.mapNotNull<Long, BackendProjectViewNodeModel<T>> { state?.getNodeById(it) }
    uiDataSnapshotForSelection(selectedNodes, sink, snapshot)
    // stuff that could be useful for backend-only code, not in the monolith
    sink[PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR] = FileEditorManager.getInstance(project).selectedEditor
  }

  protected open fun uiDataSnapshotForSelection(
    selectedNodes: List<BackendProjectViewNodeModel<T>>,
    sink: DataSink,
    snapshot: DataSnapshot,
  ) {
    sink[PlatformCoreDataKeys.HELP_ID] = HelpID.PROJECT_VIEWS
    sink[LangDataKeys.IDE_VIEW] = MyIdeView(selectedNodes)
    sink[PlatformCoreDataKeys.SELECTED_ITEMS] = selectedNodes.map { it.userObject as Any }.toTypedArray()
    // stuff that could be useful for backend-only code, not in the monolith
    val navigatables = selectedNodes.mapNotNull { it.userObject as? Navigatable }
    if (navigatables.isNotEmpty()) {
      sink[CommonDataKeys.NAVIGATABLE_ARRAY] = navigatables.nullize()?.toTypedArray()
    }
    sink.lazy(CommonDataKeys.PSI_ELEMENT) {
      psi.extractPsiElements(selectedNodes).singleOrNull()
    }
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) {
      psi.extractPsiElements(selectedNodes).nullize()?.toTypedArray()
    }
    sink.lazy(PlatformCoreDataKeys.PROJECT_CONTEXT) {
      selectedNodes.singleOrNull()?.let { singleNode -> psi.extractProject(singleNode) }
    }
    sink.lazy(LangDataKeys.MODULE_CONTEXT) {
      selectedNodes.singleOrNull()?.let { singleNode -> psi.extractSingleModule(singleNode) }
    }
    sink.lazy(LangDataKeys.MODULE_CONTEXT_ARRAY) {
      psi.extractModules(selectedNodes).nullize()?.toTypedArray()
    }
    sink.lazy(ProjectView.UNLOADED_MODULES_CONTEXT_KEY) {
      psi.extractUnloadedModules(selectedNodes)
    }
    sink.lazy(ModuleGroup.ARRAY_DATA_KEY) {
      psi.extractModuleGroups(selectedNodes).nullize()?.toTypedArray()
    }
    sink.lazy(LibraryGroupElement.ARRAY_DATA_KEY) {
      psi.extractLibraryGroups(selectedNodes).nullize()?.toTypedArray()
    }
    sink.lazy(NamedLibraryElement.ARRAY_DATA_KEY) {
      psi.extractNamedLibraryElements(selectedNodes).nullize()?.toTypedArray()
    }
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    return null
  }

  private inner class MyIdeView(private val selectedNodes: List<BackendProjectViewNodeModel<T>>) : IdeView {
    override fun getDirectories(): Array<out PsiDirectory> {
      return psi.extractPsiDirectories(selectedNodes).toTypedArray()
    }

    override fun getOrChooseDirectory(): PsiDirectory? {
      return DirectoryChooserUtil.getOrChooseDirectory(this)
    }

    override fun selectElement(element: PsiElement) {
      currentTreeState.load()?.scheduleSelectElement(element)
    }
  }

  private inner class ProjectViewPaneTreeState(
    private val id: ProjectViewPaneId,
    private val builder: ProjectViewPaneStateBuilder,
    private val nodeProvider: ProjectViewTreeNodeProvider<T>,
  ) {
    val suspendingState: SuspendingBackendProjectViewPaneStateAccessor<T> = builder.asSuspendingBackendStateAccessor()
    val state: BackendProjectViewPaneStateAccessor<T> = builder.asBackendStateAccessor()

    private val stateUpdateRequests = Channel<StateUpdateRequest>(capacity = Channel.UNLIMITED)
    // Only the latest selection matters, so an old pending request may be dropped in favor of a newer one.
    private val selectRequests = Channel<PsiElement>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val pendingUpdates = ConcurrentHashMap<Long, ProjectViewNodeUpdateOptions>()

    private val pendingUpdatesSignal = MutableStateFlow(0L)

    // Every scheduled request carries a monotonically increasing epoch (assigned under requestLock so
    // that the channel's FIFO order matches the epoch order). appliedUpdateEpoch advances to a
    // request's epoch once it has been processed, so awaitPendingUpdates can wait for a specific point.
    private val requestLock = Any()
    private var submittedUpdateEpoch = 0L
    private val appliedUpdateEpoch = MutableStateFlow(0L)

    // The updater queue (phase 1 of awaitPendingUpdates): events queued vs. events turned into updateNode calls.
    private val submittedEventEpoch = MutableStateFlow(0L)
    private val processedEventEpoch = MutableStateFlow(0L)
    val progressReporter: ProjectViewUpdaterProgressReporter = object : ProjectViewUpdaterProgressReporter {
      override fun eventSubmitted() {
        submittedEventEpoch.update { it + 1L }
      }

      override fun eventsProcessed(count: Int) {
        processedEventEpoch.update { it + count }
      }
    }

    private val sessionFinished = CompletableDeferred<Unit>()

    private var nextId = 1L

    suspend fun run() {
      try {
        coroutineScope {
          applySettings()
          initialize()
          onStateChanged(suspendingState)
          launch(CoroutineName("Collect updates for the PV pane $id")) {
            createUpdater().continuouslyUpdatePane(this@TreeBasedProjectViewPaneModel, progressReporter)
          }
          launch(CoroutineName("Flush updates for the PV pane $id")) {
            pendingUpdatesSignal.throttle(timeMs = 50).collect {
              scheduleProcessPendingUpdates()
            }
          }
          launch(CoroutineName("Update requests for the PV pane $id")) {
            for (request in stateUpdateRequests) {
              when (request) {
                is LoadChildrenRequest -> {
                  updateChildren(request.parentId, allowLoading = true, deep = false)
                }
                is ProcessPendingUpdatesRequest -> {
                  processPendingUpdates()
                }
                is UpdateSettingsRequest -> {
                  applySettings()
                }
                is SetOptionRequest -> {
                  applyOptionChange(request.option, request.newValue)
                }
                is SelectNodeRequest -> {
                  builder.selectNode(request.nodePath)
                }
              }
              appliedUpdateEpoch.value = request.epoch
              onStateChanged(suspendingState)
            }
          }
          launch(CoroutineName("Select requests for the PV pane $id")) {
            selectRequests.consumeAsFlow().collectLatest { element ->
              selectElementImpl(element)
            }
          }
        }
      }
      finally {
        sessionFinished.complete(Unit)
      }
    }

    suspend fun awaitPendingUpdates() {
      awaitOrThrowIfFinished {
        // Phase 1: wait for the updater to turn every already-queued event into updateNode calls.
        val eventTarget = submittedEventEpoch.value
        processedEventEpoch.first { it >= eventTarget }
        // Phase 2: enqueue a barrier that drains everything currently pending (plus all prior requests),
        // then wait until it (and thus everything submitted before this call) has been applied.
        val target = scheduleProcessPendingUpdates()
        appliedUpdateEpoch.first { it >= target }
      }
    }

    private suspend fun <R> awaitOrThrowIfFinished(block: suspend () -> R): R = coroutineScope {
      val outer = this
      val watcher = launch {
        sessionFinished.await()
        outer.cancel(CancellationException("The Project View pane $id is no longer managed"))
      }
      try {
        block()
      }
      finally {
        watcher.cancel()
      }
    }

    fun scheduleLoadChildren(parentId: Long) {
      schedule { LoadChildrenRequest(it, parentId) }
    }

    fun scheduleUpdateSettings() {
      schedule { UpdateSettingsRequest(it) }
    }

    fun scheduleSetOption(option: ProjectViewPaneOption, newValue: Boolean) {
      schedule { SetOptionRequest(it, option, newValue) }
    }

    fun scheduleSelectElement(element: PsiElement) {
      selectRequests.trySend(element)
    }

    private fun scheduleProcessPendingUpdates(): Long {
      return schedule { ProcessPendingUpdatesRequest(it) }
    }

    private fun schedule(makeRequest: (epoch: Long) -> StateUpdateRequest): Long = synchronized(requestLock) {
      val epoch = ++submittedUpdateEpoch
      stateUpdateRequests.trySend(makeRequest(epoch))
      epoch
    }

    fun updateNode(nodeId: Long, options: ((ProjectViewNodeUpdateOptionsBuilder) -> Unit)?) {
      val newUpdate = ProjectViewNodeUpdateOptionsBuilderImpl()
      options?.invoke(newUpdate)
      // remove-reinsert ensures no concurrent access from the update routine
      val existingUpdate = pendingUpdates.remove(nodeId)
      val resultingUpdate = existingUpdate?.merge(newUpdate) ?: newUpdate
      pendingUpdates[nodeId] = resultingUpdate
      pendingUpdatesSignal.update { it + 1L }
    }

    suspend fun visitTree(visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action): BackendProjectViewNodeModel<T>? {
      val superRoot = suspendingState.getNodeById(SUPER_ROOT_ID) ?: return null
      val listOfMaybeSingleRoot = suspendingState.getChildren(superRoot) ?: return null
      return visitNodes(listOfMaybeSingleRoot, visitNode)
    }

    private suspend fun visitNodes(
      nodes: List<BackendProjectViewNodeModel<T>>,
      visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action,
    ): BackendProjectViewNodeModel<T>? {
      for (node in nodes) {
        when (visitNode(node)) {
          TreeVisitor.Action.INTERRUPT -> return node // found here
          TreeVisitor.Action.CONTINUE -> {
            val children = suspendingState.getChildren(node) ?: continue // the node is gone
            val resultFromChildren = visitNodes(children, visitNode)
            if (resultFromChildren != null) return resultFromChildren // found deeper
          }
          TreeVisitor.Action.SKIP_CHILDREN -> continue
          TreeVisitor.Action.SKIP_SIBLINGS -> return null
        }
      }
      return null
    }

    private suspend fun selectElementImpl(element: PsiElement) {
      // Make sure everything submitted before the selection request is reflected in the tree,
      // so that a just-created element can be found (the equivalent of the old myNodeUpdater.updateImmediately).
      awaitPendingUpdates()
      val file = readAction { if (element.isValid) PsiUtilCore.getVirtualFile(element) else null }
      val visitorProvider = createSelectNodeVisitorProvider()
      // First attempt: try to find and select the element itself.
      if (trySelect(visitorProvider.createSelectNodeVisitor(element, file))) return
      // A node may report that it doesn't contain the element even though it does contain its file
      // (reportedly the case with top-level Kotlin functions and Kotlin files),
      // so make a second attempt looking for the file only.
      if (file == null) return
      if (Registry.`is`("async.project.view.support.extra.select.disabled", false)) return
      trySelect(visitorProvider.createSelectNodeVisitor(element = null, file = file))
    }

    private suspend fun trySelect(visitor: ProjectViewSelectNodeVisitor<T>): Boolean {
      val node = visitTree { node -> visitor.visitNodeForSelect(node) } ?: return false
      val nodePath = suspendingState.getNodePathById(node.id) ?: return false // the node has just been removed
      // Route the actual state-flow emission through the single update-requests writer (see run()).
      schedule { SelectNodeRequest(it, nodePath) }
      return true
    }

    private suspend fun applySettings() {
      builder.updateSettingsState { settingsStateBuilder ->
        buildSettingsState(settingsStateBuilder)
      }
    }

    private suspend fun applyOptionChange(option: ProjectViewPaneOption, newValue: Boolean) {
      val settingsService = ProjectViewPaneSettingsService.getInstance(project)
      val changed = settingsService.isOptionSelected(option) != newValue
      // Persist the new value regardless (matches ProjectViewImpl.Option.setSelected, which always
      // writes the per-project, default and shared settings).
      settingsService.setOptionSelected(option, newValue)
      if (!changed) return
      // Refresh the settings part of the state: option check marks, cross-option enablement (e.g.
      // Abbreviate Package Names depends on Flatten Packages), and so on. The frontend menu reacts
      // to this via ProjectViewSettingsStateEvent.
      applySettings()
      when (option) {
        is ProjectViewPaneOption.FoldersAlwaysOnTop,
        is ProjectViewPaneOption.ShowScratchesAndConsoles,
        is ProjectViewPaneOption.ManualOrder -> {
          updateAll(withComparator = true)
        }
        is ProjectViewPaneOption.ShowMembers,
        is ProjectViewPaneOption.ShowModules,
        is ProjectViewPaneOption.FlattenModules,
        is ProjectViewPaneOption.FlattenPackages,
        is ProjectViewPaneOption.AbbreviatePackageNames,
        is ProjectViewPaneOption.HideEmptyMiddlePackages,
        is ProjectViewPaneOption.CompactDirectories,
        is ProjectViewPaneOption.ShowLibraryContents,
        is ProjectViewPaneOption.ShowExcludedFiles,
        is ProjectViewPaneOption.ShowVisibilityIcons -> {
          updateAll(withComparator = false)
        }
      }
    }

    private fun updateAll(withComparator: Boolean) {
      updateNode(SUPER_ROOT_ID) { it.deep = true }
      if (withComparator) {
        project.messageBus.syncPublisher(BookmarksListener.TOPIC).structureChanged(null)
      }
    }

    suspend fun initialize() {
      val root = updateRoot()
      if (root != null) {
        updateChildren(root, allowLoading = true, deep = false)
      }
    }

    private suspend fun updateRoot(): BackendProjectViewNodeModel<T>? {
      val roots = nodeProvider.getChildren(null) ?: return null
      if (roots.isEmpty()) return null
      if (roots.size != 1) {
        LOG.warn("The node provider $nodeProvider returned ${roots.size} roots. This is not supported, only the first root will be used")
      }
      val root = roots.first()
      val rootModel = getOrCreateNodeModel(root) ?: return null
      setChildrenModels(parent = null, listOf(rootModel))
      return rootModel
    }

    private suspend fun processPendingUpdates() {
      while (pendingUpdates.isNotEmpty()) {
        for (nodeId in pendingUpdates.keys) {
          val nodePath = suspendingState.getNodePathById(nodeId)
          if (nodePath == null) { // the node was removed (possibly by a previous update right here)
            pendingUpdates.remove(nodeId)
            continue
          }
          processPendingUpdatesForPath(nodePath)
        }
      }
    }

    private suspend fun processPendingUpdatesForPath(nodePath: ProjectViewNodePath) {
      // Consider the following scenario:
      // 1. We iterate over the IDs.
      // 2. At some point we run into a deep update at the level L of our path.
      // 3. The node and all its children are updated recursively, including levels from L+1 and onwards.
      // 4. At the same time, in parallel, someone request another update of some child at level > L.
      // 5. At this point, we have no way to know whether that node is recent enough:
      // it could be that the update was requested after we updated it.
      // To avoid this scenario, we first remove all existing requests, then process it.
      // This guarantees that all our updates will be at least as recent as the requests are.
      val updatesForPath = nodePath.nodeIds.map { nodeId -> nodeId to pendingUpdates.remove(nodeId) }
      for ((nodeId, updateOptions) in updatesForPath) {
        if (updateOptions == null) continue
        applyNodeUpdate(nodeId, updateOptions)
        if (updateOptions.deep) break // we've just updated all deeper nodes
      }
    }

    private suspend fun applyNodeUpdate(nodeId: Long, updateOptions: ProjectViewNodeUpdateOptions) {
      val node = suspendingState.getNodeById(nodeId) ?: return
      val newNodeModel = createUpdatedModel(node)
      if (newNodeModel == null) { // the node was removed/invalidated
        val parent = suspendingState.getParentByChildId(nodeId) ?: return
        val siblings = suspendingState.getChildren(parent) ?: return
        val childIndex = siblings.indexOf(node).takeIf { it != -1 } ?: return
        builder.removeNodeChild(parent.id, childIndex)
      }
      else {
        builder.updateNode(newNodeModel)
        if (updateOptions.deep) { // update recursively, but only already loaded children
          updateChildren(parentId = nodeId, allowLoading = false, deep = true)
        }
      }
    }

    suspend fun updateChildren(parentId: Long, allowLoading: Boolean, deep: Boolean) {
      val parentModel = suspendingState.getNodeById(parentId) ?: return
      updateChildren(parentModel, allowLoading, deep)
    }

    private suspend fun updateChildren(parentModel: BackendProjectViewNodeModel<T>, allowLoading: Boolean, deep: Boolean) {
      parentModel as ProjectViewNodeModelImpl<T>
      val newChildren = nodeProvider.getChildren(parentModel.userObject) ?: return
      val oldModels = suspendingState.getChildren(parentModel)

      if (oldModels == null && !allowLoading) return

      if (oldModels == null) { // first time loaded, must send even if empty
        setChildren(parentModel, newChildren)
        return
      }

      if (oldModels.isEmpty() && newChildren.isEmpty()) { // no change
        return
      }

      if (oldModels.isEmpty()) { // empty -> non-empty
        setChildren(parentModel, newChildren)
        return
      }

      if (newChildren.isEmpty()) { // non-empty -> empty
        removeChildren(parentModel)
        return
      }

      // Compute a map of all previously existing children.
      val oldModelsByUserObject = HashMap<T, BackendProjectViewNodeModel<T>>(oldModels.size)
      for (oldModel in oldModels) {
        oldModelsByUserObject[oldModel.userObject] = oldModel
      }

      // Now remove from the map all children that still exist.
      // At the same time, build the list of all new models and their added/updated flags.
      val newModels = mutableListOf<BackendProjectViewNodeModel<T>>()
      val newModelIsUpdatedModel = BooleanArray(newChildren.size)
      for (newChild in newChildren) {
        val oldModel = oldModelsByUserObject.remove(newChild)
        val updated: Boolean
        val newModel = if (oldModel == null) {
          updated = false
          createNewModel(newChild)
        }
        else {
          updated = true
          updateModel(oldModel, newChild)
        }
        if (newModel == null) { // null means it was invalidated before we even managed to build its model
          if (oldModel != null) { // we thought it was just updated, but since it's invalid now, it's effectively removed
            oldModelsByUserObject[oldModel.userObject] = oldModel
          } // else if both are null, it means it just appeared and went away immediately (e.g. a short-lived temp file)
        }
        else {
          newModels.add(newModel)
          newModelIsUpdatedModel[newModels.lastIndex] = updated
        }
      }

      // Now oldModelsByUserObject contain only removed children.
      for ((index, oldModel) in oldModels.withIndex()) { // "child removed" calls expect before-removal indices
        if (oldModelsByUserObject.containsKey(oldModel.userObject)) {
          builder.removeNodeChild(parentModel.id, index)
        }
      }

      // Now we can proceed with add/update events, both expect after-event indices.
      for ((index, newModel) in newModels.withIndex()) {
        if (newModelIsUpdatedModel[index]) {
          builder.updateNode(newModel)
        }
        else {
          builder.addNode(parentModel.id, index, newModel)
        }
      }

      if (deep) {
        for (newChild in newModels) { // update recursively, but don't load unless already loaded
          updateChildren(newChild, allowLoading = false, deep = true)
        }
      }
    }

    private suspend fun getOrCreateNodeModel(node: T): BackendProjectViewNodeModel<T>? {
      val existingModel = suspendingState.getNodeByUserObject(node)
      if (existingModel != null) return existingModel
      return createNewModel(node)
    }

    private suspend fun createNewModel(node: T): BackendProjectViewNodeModel<T>? {
      val id = nextId++
      return nodeProvider.createNodeModel(id, node)
    }

    private suspend fun createUpdatedModel(node: BackendProjectViewNodeModel<T>): BackendProjectViewNodeModel<T>? {
      return nodeProvider.createNodeModel(node.id, node.userObject)
    }

    private suspend fun updateModel(oldModel: BackendProjectViewNodeModel<T>, newNode: T): BackendProjectViewNodeModel<T>? {
      return nodeProvider.createNodeModel(oldModel.id, newNode)
    }

    private suspend fun setChildren(parent: BackendProjectViewNodeModel<T>?, children: List<T>) {
      setChildrenModels(parent, children.mapNotNull { getOrCreateNodeModel(it) })
    }

    private suspend fun setChildrenModels(parent: BackendProjectViewNodeModel<T>?, children: List<BackendProjectViewNodeModel<T>>) {
      builder.setNodeChildren(parent?.id ?: SUPER_ROOT_ID, children)
    }

    private suspend fun removeChildren(parent: BackendProjectViewNodeModel<T>?) {
      builder.removeNodeChildren(parent?.id ?: SUPER_ROOT_ID)
    }
  }
}

private sealed class StateUpdateRequest {
  abstract val epoch: Long
}
private data class LoadChildrenRequest(override val epoch: Long, val parentId: Long) : StateUpdateRequest()
private data class ProcessPendingUpdatesRequest(override val epoch: Long) : StateUpdateRequest()
private data class UpdateSettingsRequest(override val epoch: Long) : StateUpdateRequest()
private data class SetOptionRequest(override val epoch: Long, val option: ProjectViewPaneOption, val newValue: Boolean) : StateUpdateRequest()
private data class SelectNodeRequest(override val epoch: Long, val nodePath: ProjectViewNodePath) : StateUpdateRequest()

private val LOG = logger<TreeBasedProjectViewPaneModel<*>>()
