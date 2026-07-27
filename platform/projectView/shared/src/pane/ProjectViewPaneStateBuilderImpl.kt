// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.projectView.pane

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValueImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilder
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilderImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.platform.projectView.settings.toSettingValue
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

internal class ProjectViewPaneStateBuilderImpl(
  paneId: ProjectViewPaneId,
) : ProjectViewPaneStateBuilder {
  private class State(private val paneId: ProjectViewPaneId) : MutableStateWithIncrementalUpdates<ProjectViewPaneStateEvent> {
    private var actionState: ProjectViewPaneSettingsStateDTO? = null
    private val superRoot = Node(null, SuperRootModel as ProjectViewNodeModelImpl<*>)
    private val nodeById = ConcurrentHashMap<Long, Node>().also { it[SUPER_ROOT_ID] = superRoot }
    private val nodeByUserObject = hashMapOf<Any, Node>()

    override suspend fun applyUpdate(update: ProjectViewPaneStateEvent): ProjectViewPaneStateEvent? {
      LOG.debug { "Handling update: $update" }
      when (update) {
        is ProjectViewClearStateEvent -> {
          actionState = null
          nodeById.clear()
          nodeById[SUPER_ROOT_ID] = superRoot
          nodeByUserObject.clear()
          superRoot.children = null
        }
        is ProjectViewChildrenLoaded -> {
          val parent = nodeById[update.parentId] ?: return null
          val children = update.children.mapTo(mutableListOf()) { Node(parent, it) }
          parent.children = children
          for (child in children) {
            addNode(child.model.id, child)
          }
        }
        is ProjectViewNodeAdded -> {
          val parent = nodeById[update.parentId] ?: return null
          val newNode = Node(parent, update.model, mutableListOf())
          parent.children?.add(update.index, newNode)
          addNode(newNode.model.id, newNode)
        }
        is ProjectViewChildRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          val removedNode = parent.children?.removeAt(update.index) ?: return null
          removeNode(removedNode.model.id)
        }
        is ProjectViewChildrenRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          parent.children?.forEach { child ->
            removeNode(child.model.id)
          }
          parent.children?.clear()
        }
        is ProjectViewNodeUpdated -> {
          val node = nodeById[update.model.id] ?: return null
          node.model = update.model
        }
        is ProjectViewSettingsStateEvent -> {
          actionState = update.settingsState
        }
      }
      LOG.debug { "Handled update: $update" }
      if (LOG.isTraceEnabled) {
        dumpState()
      }
      return update
    }

    private fun addNode(id: Long, node: Node) {
      nodeById[id] = node
      val userObject = node.model.userObject
      if (userObject != null) { // null on the frontend
        nodeByUserObject[userObject] = node
      }
    }

    private fun removeNode(id: Long) {
      val removed = nodeById.remove(id)
      val userObject = removed?.model?.userObject
      if (userObject != null) {
        nodeByUserObject.remove(userObject)
      }
    }

    override suspend fun takeSnapshot(): List<ProjectViewPaneStateEvent> {
      LOG.debug("Taking snapshot")
      val result = ArrayList<ProjectViewPaneStateEvent>(nodeById.size)
      addActionStates(result)
      addTreeSnapshot(result)
      return result
    }

    private fun addActionStates(result: ArrayList<ProjectViewPaneStateEvent>) {
      val actionState = actionState
      if (actionState != null) {
        result.add(ProjectViewSettingsStateEvent(actionState))
      }
    }

    private fun addTreeSnapshot(result: ArrayList<ProjectViewPaneStateEvent>) {
      val bfsQueue = ArrayDeque<Long>()
      bfsQueue.addLast(SUPER_ROOT_ID)
      while (true) {
        val parentId = bfsQueue.removeFirstOrNull() ?: break
        val children = nodeById.getValue(parentId).children ?: continue
        result.add(ProjectViewChildrenLoaded(parentId, children.map { it.model }))
        for (child in children) {
          bfsQueue.addLast(child.model.id)
        }
      }
    }
    
    private fun dumpState() {
      val state = buildString { 
        dumpState(id = SUPER_ROOT_ID, level = 0)
      }
      LOG.trace { "The current state is:\n$state" }
    }
    
    private fun StringBuilder.dumpState(id: Long, level: Int) {
      val node = nodeById.getValue(id)
      append(" ".repeat(level))
      append("[").append(id).append("] ")
      append(node.model.presentation.mainText)
      append("\n")
      val children = node.children
      if (children.isNullOrEmpty()) return
      for (child in children) {
        dumpState(child.model.id, level + 1)
      }
    }

    fun <T> asBackendStateAccessor(): BackendProjectViewPaneStateAccessor<T> {
      return BackendProjectViewPaneStateAccessorImpl(nodeById)
    }

    fun <T> asSuspendingBackendStateAccessor(flowProducer: IncrementalUpdateFlowProducer<ProjectViewPaneStateEvent, State>): SuspendingBackendProjectViewPaneStateAccessor<T> {
      return SuspendingBackendProjectViewPaneStateAccessorImpl(paneId, flowProducer)
    }

    fun asSettingsAccessor(): ProjectViewPaneSettingsAccessor {
      return ProjectViewPaneSettingsAccessorImpl { actionState }
    }

    private class BackendProjectViewPaneStateAccessorImpl<T>(
      private val nodeById: ConcurrentHashMap<Long, Node>,
    ) : BackendProjectViewPaneStateAccessor<T> {
      @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
      override fun getNodeById(id: Long): BackendProjectViewNodeModel<T>? {
        return nodeById[id]?.model as BackendProjectViewNodeModel<T>?
      }
    }

    private class SuspendingBackendProjectViewPaneStateAccessorImpl<T>(
      private val paneId: ProjectViewPaneId,
      private val flowProducer: IncrementalUpdateFlowProducer<ProjectViewPaneStateEvent, State>,
    ) : SuspendingBackendProjectViewPaneStateAccessor<T> {
      @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
      override suspend fun getNodeById(id: Long): BackendProjectViewNodeModel<T>? = withState { state ->
        state.nodeById[id]?.model as BackendProjectViewNodeModel<T>?
      }

      override suspend fun getNodePathById(id: Long): ProjectViewNodePath? = withState { state ->
        val ids = mutableListOf<Long>()
        var nodeId = id
        while (nodeId != SUPER_ROOT_ID) {
          ids += nodeId
          nodeId = state.nodeById[nodeId]?.parent?.model?.id ?: return@withState null // was removed
        }
        projectViewNodePath(paneId, ids.reversed())
      }

      @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
      override suspend fun getNodeByUserObject(userObject: T): BackendProjectViewNodeModel<T>? = withState { state ->
        state.nodeByUserObject[userObject as Any]?.model as BackendProjectViewNodeModel<T>?
      }

      @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
      override suspend fun getParentByChildId(childId: Long): BackendProjectViewNodeModel<T>? = withState { state ->
        state.nodeById[childId]?.parent?.model as BackendProjectViewNodeModel<T>?
      }

      @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
      override suspend fun getChildren(parent: BackendProjectViewNodeModel<T>?): List<BackendProjectViewNodeModel<T>>? = withState { state ->
        val parentId = parent?.id ?: SUPER_ROOT_ID
        val parent = state.nodeById[parentId] ?: return@withState null
        parent.children?.map { it.model as BackendProjectViewNodeModel<T> }
      }

      private suspend inline fun <T> withState(noinline accessor: (State) -> T): T = flowProducer.accessState(accessor)
    }
  }
  
  private val state = State(paneId)

  private val flowProducer = IncrementalUpdateFlowProducer(state)

  fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = flowProducer.getIncrementalUpdateFlow()

  @Suppress("UNCHECKED_CAST") // cast to the only implementation of a sealed interface
  override suspend fun setNodeChildren(
    parentId: Long,
    children: List<ProjectViewNodeModel>,
  ) {
    updateState(ProjectViewChildrenLoaded(parentId, children as List<ProjectViewNodeModelImpl<*>>))
  }

  override suspend fun addNode(
    parentId: Long,
    index: Int,
    nodeModel: ProjectViewNodeModel,
  ) {
    updateState(ProjectViewNodeAdded(parentId, index, nodeModel as ProjectViewNodeModelImpl<*>))
  }

  override suspend fun updateNode(nodeModel: ProjectViewNodeModel) {
    updateState(ProjectViewNodeUpdated(nodeModel as ProjectViewNodeModelImpl<*>))
  }

  override suspend fun removeNodeChildren(parentId: Long) {
    updateState(ProjectViewChildrenRemoved(parentId))
  }

  override suspend fun removeNodeChild(parentId: Long, index: Int) {
    updateState(ProjectViewChildRemoved(parentId, index))
  }

  override suspend fun updateSettingsState(build: (ProjectViewPaneSettingsStateBuilder) -> Unit) {
    val builder = ProjectViewPaneSettingsStateBuilderImpl()
    build(builder)
    updateState(ProjectViewSettingsStateEvent(builder.build()))
  }

  override suspend fun clear() {
    updateState(ProjectViewClearStateEvent)
  }

  private suspend fun updateState(update: ProjectViewPaneStateEvent) {
    flowProducer.handleUpdate(update)
  }

  override fun <T> asBackendStateAccessor(): BackendProjectViewPaneStateAccessor<T> {
    return state.asBackendStateAccessor()
  }

  @Suppress("UNCHECKED_CAST") // the platform has no idea about types, common sense the implementations is the type safety guarantee
  override fun <T> asSuspendingBackendStateAccessor(): SuspendingBackendProjectViewPaneStateAccessor<T> {
    return state.asSuspendingBackendStateAccessor(flowProducer)
  }

  override fun asSettingsAccessor(): ProjectViewPaneSettingsAccessor {
    return state.asSettingsAccessor()
  }
}

private class ProjectViewPaneSettingsAccessorImpl(
  private val stateGetter: () -> ProjectViewPaneSettingsStateDTO?
) : ProjectViewPaneSettingsAccessor {
  override fun isOptionSelected(option: ProjectViewPaneOption): Boolean {
    val optionState = stateGetter()?.optionStates[(option as ProjectViewPaneOptionImpl).dto]
    return optionState?.isSelected == true && optionState.isEnabled // disabled options are considered effectively off
  }

  override fun getSortKey(): ProjectViewPaneSortKey {
    return stateGetter()?.sortKeyState?.sortKey?.toSettingValue() ?: ProjectViewPaneSortKey.byName()
  }

  override fun getFileNesting(): ProjectViewPaneFileNestingValue {
    val fileNesting = stateGetter()?.fileNestingState ?: return ProjectViewPaneFileNestingValueImpl(false, emptyList())
    return ProjectViewPaneFileNestingValueImpl(
      isFileNestingOn = fileNesting.isFileNestingOn,
      nestingRules = fileNesting.activeRules.map { it.toNestingRule() },
    )
  }
}

private data class Node(val parent: Node?, var model: ProjectViewNodeModelImpl<*>, var children: MutableList<Node>? = null)

private val LOG = logger<ProjectViewPaneStateBuilder>()
