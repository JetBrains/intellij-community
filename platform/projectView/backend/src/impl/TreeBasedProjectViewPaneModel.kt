// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneLoadChildrenOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneSelectionOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilder
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SelectInRequest
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilder
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.platform.projectView.settings.allProjectViewPaneOptions
import com.intellij.platform.projectView.settings.allProjectViewPaneSortKeys
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewTreeNodeProvider<T> {
  suspend fun getChildren(parent: T?): List<T>?
  suspend fun createNodeModel(id: Long, node: T): BackendProjectViewNodeModel<T>?
}

@ApiStatus.Experimental
abstract class TreeBasedProjectViewPaneModel<T>(
  protected val project: Project,
  private val nodeProvider: ProjectViewTreeNodeProvider<T>,
) : ProjectViewPaneModel {
  private val stateUpdateRequests = Channel<StateUpdateRequest>(capacity = Channel.BUFFERED)

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

  override suspend fun manageState(builder: ProjectViewPaneStateBuilder) {
    builder.updateSettingsState { settingsStateBuilder ->
      buildSettingsState(settingsStateBuilder)
    }
    val state = ProjectViewPaneTreeState(nodeProvider, builder)
    state.initialize()
    for (stateUpdateRequest in stateUpdateRequests) {
      when (stateUpdateRequest) {
        is LoadChildrenRequest -> {
          state.updateChildren(stateUpdateRequest.parentId)
        }
      }
    }
  }

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

  override suspend fun setSelected(
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

  override suspend fun navigate(nodeId: Long, options: ProjectViewPaneNavigateOptions) {
  }

  override suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean) {
  }

  override suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey) {
  }

  override suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue) {
    ProjectViewFileNestingService.getInstance().setRules(fileNestingValue.nestingRules)
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    return null
  }
}

private sealed class StateUpdateRequest
private data class LoadChildrenRequest(val parentId: Long) : StateUpdateRequest()

private class ProjectViewPaneTreeState<T>(
  private val nodeProvider: ProjectViewTreeNodeProvider<T>,
  private val builder: ProjectViewPaneStateBuilder,
) {
  private val state = builder.asBackendStateAccessor<T>()
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
