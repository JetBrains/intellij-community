// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.impl

import com.intellij.ide.DataManager
import com.intellij.ide.DeleteProvider
import com.intellij.ide.IdeView
import com.intellij.ide.SelectInContext
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.projectView.HelpID
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.SelectableTreeStructureProvider
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.idea.AppMode
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.BackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneCutCopyPasteDeleteHandler
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SelectByContext
import com.intellij.platform.projectView.pane.SelectByEditor
import com.intellij.platform.projectView.pane.SelectInRequest
import com.intellij.platform.projectView.pane.SuspendingBackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilder
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.platform.projectView.settings.allProjectViewPaneOptions
import com.intellij.platform.projectView.settings.allProjectViewPaneSortKeys
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.containers.nullize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Experimental
interface ProjectViewTreeNodeProvider<T> {
  suspend fun getChildren(parent: T?): List<T>?
  suspend fun getNodeModelFlow(id: Long, node: T): Flow<BackendProjectViewNodeModel<T>>
}

@ApiStatus.Experimental
abstract class TreeBasedProjectViewPaneModel<T : Any>(protected val project: Project) : ProjectViewPaneModel {
  private val currentTreeState = AtomicReference<ProjectViewPaneTreeState?>(null)

  override val cutCopyPasteDeleteHandler: ProjectViewPaneCutCopyPasteDeleteHandler = MyCutCopyPasteDeleteHandler()

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
    currentTreeState.load()?.scheduleSetSortKey(sortKeyValue)
  }

  override suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue) {
    ProjectViewFileNestingService.getInstance().setRules(fileNestingValue.nestingRules)
  }

  suspend fun visitTree(allowLoading: Boolean, visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action): BackendProjectViewNodeModel<T>? {
    return currentTreeState.load()?.visitTree(allowLoading = allowLoading, visitNode)
  }

  fun updateNode(nodeId: Long, options: ((ProjectViewNodeUpdateOptionsBuilder) -> Unit)? = null) {
    currentTreeState.load()?.updateNode(nodeId, options)
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    // Note: no CUT/COPY/PASTE_PROVIDER here. Those are published by the frontend pane, which delegates the
    // work back to performCopy/performCut/performPaste; see FrontendProjectViewCopyPasteProvider.
    val selectedIds = snapshot[PROJECT_VIEW_SELECTED_NODE_IDS_KEY] ?: emptyList()
    val selectedNodes = selectedIds.mapNotNull<Long, BackendProjectViewNodeModel<T>> { state?.getNodeById(it) }
    uiDataSnapshotForSelection(selectedNodes, sink, snapshot)
    // stuff that could be useful for backend-only code, not in the monolith
    sink[PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR] = FileEditorManager.getInstance(project).selectedEditor
  }

  protected open fun uiDataSnapshotForSelection(
    selectedNodes: List<BackendProjectViewNodeModel<T>>,
    sink: DataSink,
    snapshot: DataSnapshot?,
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
    sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
      createDeleteProvider(selectedNodes)
    }
  }

  /** The same choice [com.intellij.ide.projectView.impl.AbstractProjectViewPane] makes for its own selection. */
  private fun createDeleteProvider(selectedNodes: List<BackendProjectViewNodeModel<T>>): DeleteProvider? {
    if (selectedNodes.isEmpty()) return null
    if (psi.extractModules(selectedNodes).isNotEmpty() || psi.extractUnloadedModules(selectedNodes).isNotEmpty()) {
      return ModuleDeleteProvider.getInstance()
    }
    // TODO the legacy pane additionally offers DetachLibraryDeleteProvider for library nodes, which needs
    //  getSelectedLibrary() and DetachLibraryDeleteProvider, both internal to intellij.platform.lang.impl.
    return ProjectViewNodeDeleteProvider(
      elements = psi.extractPsiElements(selectedNodes),
      isHideEmptyMiddlePackages = ProjectViewPaneSettingsService.getInstance(project)
        .isOptionSelected(ProjectViewPaneOptionImpl.HideEmptyMiddlePackages),
    )
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    return currentTreeState.load()?.findNodeForSelectIn(selectInRequest)
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
  
  private inner class MyCutCopyPasteDeleteHandler : ProjectViewPaneCutCopyPasteDeleteHandler {

    override suspend fun performCopy(nodeIds: List<Long>) {
      val dataContext = selectionDataContextByNodeIds(nodeIds) ?: return
      DataContextCutCopyPasteDeleteHandler.copy(dataContext)
    }

    override suspend fun performCut(nodeIds: List<Long>) {
      val dataContext = selectionDataContextByNodeIds(nodeIds) ?: return
      DataContextCutCopyPasteDeleteHandler.cut(dataContext)
    }

    override suspend fun performPaste(nodeIds: List<Long>) {
      val dataContext = selectionDataContextByNodeIds(nodeIds) ?: return
      DataContextCutCopyPasteDeleteHandler.paste(dataContext)
    }

    override suspend fun performDelete(nodeIds: List<Long>) {
      val dataContext = selectionDataContextByNodeIds(nodeIds) ?: return
      DataContextCutCopyPasteDeleteHandler.delete(dataContext)
    }

    private fun selectionDataContextByNodeIds(nodeIds: List<Long>): DataContext? {
      val selectedNodes = selectedNodes(nodeIds)
      if (selectedNodes.isEmpty()) return null
      return selectionDataContext(selectedNodes)
    }

    private fun selectedNodes(nodeIds: List<Long>): List<BackendProjectViewNodeModel<T>> {
      val state = state ?: return emptyList()
      return nodeIds.mapNotNull { state.getNodeById(it) }
    }

    /**
     * The backend equivalent of the data context the monolith Project View would have for this selection.
     *
     * Needed because the copy/paste/delete handlers are all data-context based (see
     * [DataContextCutCopyPasteDeleteHandler]), while the frontend sends node IDs only. [PlatformCoreDataKeys.MODULE]
     * and [LangDataKeys.PASTE_TARGET_PSI_ELEMENT] are set explicitly, because in the monolith they are
     * derived by [com.intellij.openapi.actionSystem.UiDataRule]s, which only run for component-based
     * (`PreCachedDataContext`) contexts and therefore not for this one.
     */
    private fun selectionDataContext(selectedNodes: List<BackendProjectViewNodeModel<T>>): DataContext =
      CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        sink[CommonDataKeys.PROJECT] = project
        uiDataSnapshotForSelection(selectedNodes, sink, snapshot = null)
        sink.lazy(LangDataKeys.PASTE_TARGET_PSI_ELEMENT) {
          // The same as PasteTargetRule, which derives the paste target from the single selected element.
          psi.extractPsiElements(selectedNodes).singleOrNull()
        }
        sink.lazy(PlatformCoreDataKeys.MODULE) {
          val singleNode = selectedNodes.singleOrNull() ?: return@lazy null
          psi.extractSingleModule(singleNode)
          ?: psi.extractPsiElements(listOf(singleNode)).singleOrNull()?.let { ModuleUtilCore.findModuleForPsiElement(it) }
        }
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
    
    private lateinit var modelComputationScope: CoroutineScope
    private val modelComputations = ConcurrentHashMap<Long, NodeModelComputation>()

    private val sessionFinished = CompletableDeferred<Unit>()

    private var nextId = 1L

    suspend fun run() {
      try {
        coroutineScope {
          modelComputationScope = childScope("Async model computations for the PV pane $id")
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
                is UpdateNodeModelRequest<*> -> {
                  builder.updateNode(request.model)
                }
                is UpdateSettingsRequest -> {
                  applySettings()
                }
                is SetOptionRequest -> {
                  applyOptionChange(request.option, request.newValue)
                }
                is SetSortKeyRequest -> {
                  applySortKeyChange(request.sortKey)
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

    fun scheduleSetSortKey(sortKey: ProjectViewPaneSortKey) {
      schedule { SetSortKeyRequest(it, sortKey) }
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

    suspend fun visitTree(allowLoading: Boolean, visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action): BackendProjectViewNodeModel<T>? {
      val superRoot = suspendingState.getNodeById(SUPER_ROOT_ID) ?: return null
      val listOfMaybeSingleRoot = getOrMaybeLoadChildren(superRoot, allowLoading) ?: return null
      return visitNodes(allowLoading, listOfMaybeSingleRoot, visitNode)
    }

    private suspend fun visitNodes(
      allowLoading: Boolean,
      nodes: List<BackendProjectViewNodeModel<T>>,
      visitNode: suspend (BackendProjectViewNodeModel<T>) -> TreeVisitor.Action,
    ): BackendProjectViewNodeModel<T>? {
      for (node in nodes) {
        when (visitNode(node)) {
          TreeVisitor.Action.INTERRUPT -> return node // found here
          TreeVisitor.Action.CONTINUE -> {
            val children = getOrMaybeLoadChildren(node, allowLoading) ?: continue // the node is gone
            val resultFromChildren = visitNodes(allowLoading, children, visitNode)
            if (resultFromChildren != null) return resultFromChildren // found deeper
          }
          TreeVisitor.Action.SKIP_CHILDREN -> continue
          TreeVisitor.Action.SKIP_SIBLINGS -> return null
        }
      }
      return null
    }

    private suspend fun getOrMaybeLoadChildren(parent: BackendProjectViewNodeModel<T>, allowLoading: Boolean): List<BackendProjectViewNodeModel<T>>? {
      var existingChildren = suspendingState.getChildren(parent)
      if (existingChildren == null && allowLoading) {
        scheduleLoadChildren(parent.id)
        while (existingChildren == null) {
          val currentEpoch = appliedUpdateEpoch.value
          appliedUpdateEpoch.first { it > currentEpoch }
          if (suspendingState.getNodeById(parent.id) == null) return null // the parent is gone
          existingChildren = suspendingState.getChildren(parent)
        }
      }
      return existingChildren
    }

    private suspend fun selectElementImpl(element: PsiElement) {
      // Make sure everything submitted before the selection request is reflected in the tree,
      // so that a just-created element can be found (the equivalent of the old myNodeUpdater.updateImmediately).
      awaitPendingUpdates()
      val file = readAction { if (element.isValid) PsiUtilCore.getVirtualFile(element) else null }
      val nodePath = findNodePathForTarget(element, file) ?: return
      // Route the actual state-flow emission through the single update-requests writer (see run()).
      schedule { SelectNodeRequest(it, nodePath) }
    }

    suspend fun findNodeForSelectIn(request: SelectInRequest): ProjectViewNodePath? {
      // Reproduce the classic Project View "Select In" (ProjectViewSelectInTarget / SelectInTargetPsiWrapper):
      // derive the target the same way the legacy code does (which happens before the pane is even touched),
      // then make the tree reflect everything submitted so far (the equivalent of the old updateImmediately),
      // then search. The result is the same node the legacy Project View would select.
      val target = computeSelectTarget(request) ?: return null
      if (target.element == null && target.file == null) return null
      awaitPendingUpdates()
      return findNodePathForTarget(target.element, target.file)
    }

    /**
     * The two-pass search shared by [selectElementImpl] and [findNodeForSelectIn]. First try to match the element
     * itself; a node may report that it doesn't contain the element even though it does contain its file (reportedly
     * the case with top-level Kotlin functions and Kotlin files), so fall back to matching the file only. Unlike
     * [selectElementImpl] this neither awaits pending updates nor schedules a selection: it only finds the path.
     */
    private suspend fun findNodePathForTarget(element: PsiElement?, file: VirtualFile?): ProjectViewNodePath? {
      val visitorProvider = createSelectNodeVisitorProvider()
      tryFindPath(visitorProvider.createSelectNodeVisitor(element, file))?.let { return it }
      if (element == null) return null // we've already tried looking for the file only
      if (file == null) return null // no file to try
      if (Registry.`is`("async.project.view.support.extra.select.disabled", false)) return null
      return tryFindPath(visitorProvider.createSelectNodeVisitor(element = null, file = file))
    }

    private suspend fun tryFindPath(visitor: ProjectViewSelectNodeVisitor<T>): ProjectViewNodePath? {
      val node = visitTree(allowLoading = true) { node -> visitor.visitNodeForSelect(node) } ?: return null
      return suspendingState.getNodePathById(node.id) // null if the node has just been removed
    }

    private suspend fun computeSelectTarget(request: SelectInRequest): SelectTarget? = when (request) {
      is SelectByContext -> computeContextTarget(request.context)
      is SelectByEditor -> computeEditorTarget(request)
    }

    private suspend fun computeContextTarget(context: SelectInContext): SelectTarget? {
      if (!canSelectContext(context)) return null
      return normalizeContextSelector(context)
    }

    private suspend fun canSelectContext(context: SelectInContext): Boolean = readAction {
      if (project.isDisposed || !project.isInitialized) return@readAction false
      if (!context.virtualFile.isValid) return@readAction false
      val psiItem = contextPsiFile(project, context) ?: return@readAction false
      val vFile = PsiUtilCore.getVirtualFile(psiItem)?.let { BackedVirtualFile.getOriginFileIfBacked(it) }
      if (vFile == null || !vFile.isValid) return@readAction false
      ProjectViewPane.canBeSelectedInProjectView(project, vFile)
    }

    private suspend fun normalizeContextSelector(context: SelectInContext): SelectTarget? = readAction {
      if (project.isDisposed) return@readAction null
      normalizeSelector(context.virtualFile, context.selectorInFile)
    }

    private suspend fun computeEditorTarget(request: SelectByEditor): SelectTarget? {
      val useLastFocused = request.considerOnlyLastFocusedEditor && AppMode.isMonolith()
      val editors = withContext(Dispatchers.UI) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val lastFocused = if (useLastFocused) (fileEditorManager as? FileEditorManagerImpl)?.getLastFocusedEditor() else null
        if (lastFocused != null) {
          listOf(lastFocused)
        }
        else {
          (listOf(fileEditorManager.selectedEditor) + fileEditorManager.selectedEditors).filterNotNull()
        }
      }
      for (fileEditor in editors) {
        if (
          !request.isInvokedManually &&
          AdvancedSettings.getBoolean("project.view.do.not.autoscroll.to.libraries") &&
          readAction { fileEditor.file?.let { file -> ProjectFileIndex.getInstance(project).isInLibrary(file) } == true }
        ) {
          if (LOG.isDebugEnabled) {
            LOG.debug("Skipping $fileEditor because the file is in a library and autoscroll to libraries is off")
          }
          continue
        }
        val target = computeEditorTargetFor(fileEditor)
        if (target != null) return target // stop at the first editor with a PSI file, like the classic code
      }
      return null
    }

    private suspend fun computeEditorTargetFor(fileEditor: FileEditor): SelectTarget? {
      // A data context may provide its own SelectInContext (SelectInProjectViewImpl.createSelectInContext).
      val providedContext = withContext(Dispatchers.UI) {
        DataManager.getInstance().getDataContext(fileEditor.component).getData(SelectInContext.DATA_KEY)
      }
      if (providedContext != null) return normalizeContextSelector(providedContext)
      val psiFile = editorPsiFile(fileEditor) ?: return null // no PSI file => skip (getPsiFilePointer == null)
      // EditorSelectInContext.getSelectorInFile is the caret element;
      // SimpleSelectInContext.getSelectorInFile is the file itself.
      val selector: PsiElement = if (fileEditor is TextEditor) editorCaretElement(fileEditor, psiFile) ?: psiFile else psiFile
      return readAction {
        if (project.isDisposed) return@readAction null
        normalizeSelector(psiFile.viewProvider.virtualFile, selector)
      }
    }

    private suspend fun editorPsiFile(fileEditor: FileEditor): PsiFile? {
      if (!withContext(Dispatchers.UI) { fileEditor.isValid }) return null
      return if (fileEditor is TextEditor) {
        val editor = fileEditor.editor
        if (withContext(Dispatchers.UI) { editor.isDisposed }) return null
        readAction { PsiDocumentManager.getInstance(project).getPsiFile(editor.document) }
      }
      else {
        val file = withContext(Dispatchers.UI) { fileEditor.file } ?: return null
        readAction { if (file.isValid) PsiManager.getInstance(project).findFile(file) else null }
      }
    }

    private suspend fun editorCaretElement(fileEditor: TextEditor, psiFile: PsiFile): PsiElement? {
      val editor = fileEditor.editor
      val offset = withContext(Dispatchers.UI) { if (editor.isDisposed) -1 else editor.caretModel.offset }
      if (offset < 0) return null
      return constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
        if (psiFile.isValid) psiFile.findElementAt(offset) else null
      }
    }

    private fun normalizeSelector(file: VirtualFile, rawSelector: Any?): SelectTarget? {
      val selector = rawSelector ?: PsiUtilCore.findFileSystemItem(project, file)
      if (selector !is PsiElement) return SelectTarget(element = null, file = file) // non-PSI (or no) selector => file-only search
      if (!selector.isValid) return null // an invalid PSI selector: the classic code throws, we treat it as "no target"
      val original = selector.originalElement
      return if (original != null && original.isValid) normalizeElement(original) else null
    }

    private fun normalizeElement(element: PsiElement): SelectTarget {
      var topLevel: PsiElement? = null
      val providers = DumbService.getInstance(project).filterByDumbAwareness(TreeStructureProvider.EP.getExtensions(project))
      for (provider in providers) {
        if (provider is SelectableTreeStructureProvider) {
          topLevel = provider.getTopLevelElement(element)
        }
        if (topLevel != null) {
          if (!topLevel.isValid) return SelectTarget(element = null, file = null) // classic throws; treat as "no target"
          break
        }
      }
      val toSelect = findElementToSelect(element, topLevel) ?: return SelectTarget(element = null, file = null)
      val vFile = PsiUtilCore.getVirtualFile(toSelect)?.let { BackedVirtualFile.getOriginFileIfBacked(it) }
      return SelectTarget(toSelect, vFile)
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

    private suspend fun applySortKeyChange(sortKey: ProjectViewPaneSortKey) {
      val settingsService = ProjectViewPaneSettingsService.getInstance(project)
      val changed = settingsService.getSortKey() != sortKey
      // Persist regardless (matches ProjectViewImpl.setSortKey, which always writes the per-project,
      // default and shared settings).
      settingsService.setSortKey(sortKey)
      if (!changed) return
      // Refresh the settings DTO (selected/available sort keys) so the frontend menu reflects the change,
      // then rebuild the tree with the new comparator.
      applySettings()
      updateAll(withComparator = true)
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
      LOG.debug { "Updating the children of ID = ${parentModel.id}" }
      parentModel as ProjectViewNodeModelImpl<T>
      val newChildren = nodeProvider.getChildren(if (parentModel.id == SUPER_ROOT_ID) null else parentModel.userObject) ?: return
      LOG.trace { "The new children are $newChildren" }
      val oldModels = suspendingState.getChildren(parentModel)

      if (oldModels == null && !allowLoading) return

      if (oldModels == null) { // first time loaded, must send even if empty
        LOG.trace { "The children were loaded for the first time, applying as-is" }
        setChildren(parentModel, newChildren)
        return
      }

      if (oldModels.isEmpty() && newChildren.isEmpty()) { // no change
        LOG.trace { "The children list remains empty" }
        return
      }

      if (oldModels.isEmpty()) { // empty -> non-empty
        LOG.trace { "The children list becomes non-empty" }
        setChildren(parentModel, newChildren)
        return
      }

      if (newChildren.isEmpty()) { // non-empty -> empty
        LOG.trace { "The children list becomes empty" }
        removeChildren(parentModel)
        return
      }

      LOG.trace { "The children list changes, computing the delta" }

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

      LOG.trace { "Removed children: $oldModels" }

      // Now oldModelsByUserObject contains only removed children. Remove them in descending index
      // order, so that the remaining (lower) indices stay valid as the list shrinks.
      for (index in oldModels.indices.reversed()) {
        if (oldModelsByUserObject.containsKey(oldModels[index].userObject)) {
          builder.removeNodeChild(parentModel.id, index)
        }
      }

      LOG.trace { "Remaining and new children: $oldModels" }

      // Now establish the new order left to right. New children are inserted at their target index;
      // surviving children are moved to their target index (which also refreshes their model, and is a
      // no-op reposition if they're already there). Processing ascending indices guarantees the wanted
      // node currently sits at a position >= index, so placing it at index yields the correct final order.
      for ((index, newModel) in newModels.withIndex()) {
        if (newModelIsUpdatedModel[index]) {
          builder.moveNodeChild(parentModel.id, newModel, index)
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
      return createNodeModel(id, node)
    }

    private suspend fun createUpdatedModel(node: BackendProjectViewNodeModel<T>): BackendProjectViewNodeModel<T>? {
      if (node.id == SUPER_ROOT_ID) return state.getNodeById(SUPER_ROOT_ID) // immutable, reuse (and avoid NPE, because userObject is null)
      return createNodeModel(node.id, node.userObject)
    }

    private suspend fun updateModel(oldModel: BackendProjectViewNodeModel<T>, newNode: T): BackendProjectViewNodeModel<T>? {
      return createNodeModel(oldModel.id, newNode)
    }

    private suspend fun createNodeModel(id: Long, node: T): BackendProjectViewNodeModel<T>? {
      modelComputations.remove(id)?.cancelAndJoin()
      val existingModel = suspendingState.getNodeByUserObject(node)
      if (existingModel == null) {
        // Return the first value, compute the full presentation async.
        val computation = NodeModelComputation(id, node)
        modelComputations[id] = computation
        return computation.firstValue()
      }
      else {
        // When updating an existing presentation, skip draft ones to prevent flickering
        // (old full presentation -> new draft presentation -> new full presentation).
        return nodeProvider.getNodeModelFlow(id, node).lastOrNull()
      }
    }

    private suspend fun setChildren(parent: ProjectViewNodeModelImpl<T>?, children: List<T>) {
      setChildrenModels(parent, children.mapNotNull { getOrCreateNodeModel(it) })
    }

    private suspend fun setChildrenModels(parent: ProjectViewNodeModelImpl<T>?, children: List<BackendProjectViewNodeModel<T>>) {
      if (children.isNotEmpty() && parent != null) {
        ensureNotLeaf(parent)
      }
      builder.setNodeChildren(parent?.id ?: SUPER_ROOT_ID, children)
    }

    private suspend fun ensureNotLeaf(node: ProjectViewNodeModelImpl<T>) {
      if (node.presentation.isLeaf) {
        LOG.debug { "The node ID = ${node.id} will have children now, making sure it's not a leaf" }
        builder.updateNode(buildProjectViewNodeModel(node.id, node.userObject) { nodeBuilder ->
          nodeBuilder.setModel(node)
          nodeBuilder.buildPresentation { presentationBuilder ->
            presentationBuilder.setLeaf(false)
          }
        })
      }
    }

    private suspend fun removeChildren(parent: BackendProjectViewNodeModel<T>?) {
      builder.removeNodeChildren(parent?.id ?: SUPER_ROOT_ID)
    }
    
    private inner class NodeModelComputation(id: Long, node: T) {
      private val firstValue = CompletableDeferred<BackendProjectViewNodeModel<T>?>()
      
      private val job = modelComputationScope.launch {
        var first = true
        nodeProvider.getNodeModelFlow(id, node).collect { model ->
          if (first) {
            first = false
            firstValue.complete(model)
          }
          else {
            schedule { epoch ->
              UpdateNodeModelRequest(epoch, id, model)
            }
          }
        }
      }.also { job ->
        job.invokeOnCompletion {
          modelComputations.remove(id, this@NodeModelComputation)
          firstValue.complete(null)
        }
      }
      
      suspend fun firstValue(): BackendProjectViewNodeModel<T>? = firstValue.await()
      
      suspend fun cancelAndJoin() {
        job.cancelAndJoin()
      }
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
private data class SetSortKeyRequest(override val epoch: Long, val sortKey: ProjectViewPaneSortKey) : StateUpdateRequest()
private data class SelectNodeRequest(override val epoch: Long, val nodePath: ProjectViewNodePath) : StateUpdateRequest()
private data class UpdateNodeModelRequest<T>(override val epoch: Long, val id: Long, val model: BackendProjectViewNodeModel<T>) : StateUpdateRequest()

private val LOG = logger<TreeBasedProjectViewPaneModel<*>>()

/** The (element, file) pair to look for in the tree, as computed from a [SelectInRequest]. */
private class SelectTarget(val element: PsiElement?, val file: VirtualFile?)

/**
 * Replica of the package-private `SelectInTargetPsiWrapper.findElementToSelect` (which is `protected static`, so it
 * can't be called from here): a file/directory selects as itself, any other element collapses to the base-language
 * PSI of its containing file, and the result is then replaced by its original (physical) element.
 */
private fun findElementToSelect(element: PsiElement, candidate: PsiElement?): PsiElement? {
  var toSelect = candidate
  if (toSelect == null) {
    toSelect = if (element is PsiFile || element is PsiDirectory) {
      element
    }
    else {
      element.containingFile?.viewProvider?.let { viewProvider -> viewProvider.getPsi(viewProvider.baseLanguage) }
    }
  }
  if (toSelect != null) {
    val original = try {
      toSelect.originalElement
    }
    catch (_: IndexNotReadyException) {
      null
    }
    if (original != null) {
      toSelect = original
    }
  }
  return toSelect
}

/** Replica of the package-private `SelectInTargetPsiWrapper.getContextPsiFile`. */
private fun contextPsiFile(project: Project, context: SelectInContext): PsiFileSystemItem? {
  val file = context.virtualFile
  PsiManager.getInstance(project).findFile(file)?.let { return it }
  (context.selectorInFile as? PsiFile)?.let { return it }
  if (file.isDirectory) {
    return PsiManager.getInstance(project).findDirectory(file)
  }
  return null
}
