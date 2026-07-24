// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.impl

import com.intellij.ide.IdeView
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.containers.nullize
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Experimental
interface ProjectViewTreeNodeProvider<T> {
  suspend fun getChildren(parent: T?): List<T>?
  suspend fun createNodeModel(id: Long, node: T): BackendProjectViewNodeModel<T>?
}

@ApiStatus.Experimental
abstract class TreeBasedProjectViewPaneModel<T>(protected val project: Project) : ProjectViewPaneModel {
  private val stateUpdateRequests = Channel<StateUpdateRequest>(capacity = Channel.BUFFERED)
  private val currentSuspendingState = AtomicReference<SuspendingBackendProjectViewPaneStateAccessor<T>?>(null)
  private val currentState = AtomicReference<BackendProjectViewPaneStateAccessor<T>?>(null)
  val suspendingState: SuspendingBackendProjectViewPaneStateAccessor<T>?
    get() = currentSuspendingState.load()
  val state: BackendProjectViewPaneStateAccessor<T>?
    get() = currentState.load()
  
  protected abstract val psi: ProjectViewPsiExtractor<T>

  protected open suspend fun isDefault(): Boolean = false

  protected abstract suspend fun id(): ProjectViewPaneId
  protected abstract suspend fun presentableName(): @NlsSafe String
  protected abstract suspend fun order(): Int

  private suspend fun scheduleStateUpdate(stateUpdateRequest: StateUpdateRequest) {
    stateUpdateRequests.send(stateUpdateRequest)
  }

  override suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor {
    builder.setDefault(isDefault())
    return builder.build(id(), presentableName(), order())
  }

  protected abstract suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewTreeNodeProvider<T>

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    val stateAccessor = builder.asSuspendingBackendStateAccessor<T>()
    try {
      currentState.store(builder.asBackendStateAccessor())
      currentSuspendingState.store(stateAccessor)
      updateSettings(builder)
      val nodeProvider = createNodeProvider(builder.asSettingsAccessor())
      val state = ProjectViewPaneTreeState(nodeProvider, builder, stateAccessor)
      state.initialize()
      onStateChanged(stateAccessor)
      for (stateUpdateRequest in stateUpdateRequests) {
        when (stateUpdateRequest) {
          is LoadChildrenRequest -> {
            state.updateChildren(stateUpdateRequest.parentId)
          }
          is UpdateSettingsRequest -> {
            updateSettings(builder)
          }
        }
        onStateChanged(stateAccessor)
      }
    }
    finally {
      currentSuspendingState.store(null)
      currentState.store(null)
    }
  }

  private suspend fun updateSettings(builder: ProjectViewPaneStateBuilder) {
    builder.updateSettingsState { settingsStateBuilder ->
      buildSettingsState(settingsStateBuilder)
    }
  }

  protected suspend fun updateSettings() {
    scheduleStateUpdate(UpdateSettingsRequest)
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
    scheduleStateUpdate(LoadChildrenRequest(parentId))
  }

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
  }

  override suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey) {
  }

  override suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue) {
    ProjectViewFileNestingService.getInstance().setRules(fileNestingValue.nestingRules)
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
      // TODO - need to send a request to the front
    }
  }
}

private sealed class StateUpdateRequest
private data class LoadChildrenRequest(val parentId: Long) : StateUpdateRequest()
private data object UpdateSettingsRequest : StateUpdateRequest()

private class ProjectViewPaneTreeState<T>(
  private val nodeProvider: ProjectViewTreeNodeProvider<T>,
  private val builder: ProjectViewPaneStateBuilder,
  private val state: SuspendingBackendProjectViewPaneStateAccessor<T>,
) {
  private var nextId = 1L

  suspend fun initialize() {
    val root = updateRoot()
    if (root != null) {
      updateChildren(root)
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

  suspend fun updateChildren(parentId: Long) {
    val parentModel = state.getNodeById(parentId) ?: return
    updateChildren(parentModel)
  }

  private suspend fun updateChildren(parentModel: BackendProjectViewNodeModel<T>) {
    parentModel as ProjectViewNodeModelImpl<T>
    val newChildren = nodeProvider.getChildren(parentModel.userObject) ?: return
    val oldModels = state.getChildren(parentModel)

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
  }

  private suspend fun getOrCreateNodeModel(node: T): BackendProjectViewNodeModel<T>? {
    val existingModel = state.getNodeByUserObject(node)
    if (existingModel != null) return existingModel
    return createNewModel(node)
  }

  private suspend fun createNewModel(node: T): BackendProjectViewNodeModel<T>? {
    val id = nextId++
    return nodeProvider.createNodeModel(id, node)
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

private val LOG = logger<TreeBasedProjectViewPaneModel<*>>()
