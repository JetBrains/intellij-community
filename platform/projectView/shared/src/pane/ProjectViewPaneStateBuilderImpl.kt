// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.projectView.pane

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import kotlinx.coroutines.flow.Flow

internal class ProjectViewPaneStateBuilderImpl : ProjectViewPaneStateBuilder {
  private val state = object : MutableStateWithIncrementalUpdates<ProjectViewPaneStateEvent> {
    private var actionState: ProjectViewPaneSettingsStateDTO? = null
    private val superRoot = Node(SuperRootModel)
    private val nodeById = hashMapOf<Long, Node>().also { it[SUPER_ROOT_ID] = superRoot }
    
    override suspend fun applyUpdate(update: ProjectViewPaneStateEvent): ProjectViewPaneStateEvent? {
      LOG.debug("Handling update: $update")
      when (update) {
        is ProjectViewClearStateEvent -> {
          actionState = null
          nodeById.clear()
          nodeById[SUPER_ROOT_ID] = superRoot
          superRoot.children = null
        }
        is ProjectViewChildrenLoaded -> {
          val parent = nodeById[update.parentId] ?: return null
          val children = update.children.mapTo(mutableListOf()) { Node(it) }
          parent.children = children
          for (child in children) {
            nodeById[child.model.id] = child
          }
        }
        is ProjectViewNodeAdded -> {
          val parent = nodeById[update.parentId] ?: return null
          val newNode = Node(update.model, mutableListOf())
          parent.children?.add(update.index, newNode)
          nodeById[newNode.model.id] = newNode
        }
        is ProjectViewChildRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          val removedNode = parent.children?.removeAt(update.index) ?: return null
          nodeById.remove(removedNode.model.id)
        }
        is ProjectViewChildrenRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          parent.children?.forEach { child ->
              nodeById.remove(child.model.id)
          }
          parent.children?.clear()
        }
        is ProjectViewNodeUpdated -> {
          val node = nodeById[update.model.id] ?: return null
          node.model = update.model
        }
        is ProjectViewActionStateEvent -> {
          actionState = update.actionState
        }
      }
      LOG.debug("Handled update: $update")
      if (LOG.isTraceEnabled) {
        dumpState()
      }
      return update
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
        result.add(ProjectViewActionStateEvent(actionState))
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
  }

  private data class Node(var model: ProjectViewNodeModel, var children: MutableList<Node>? = null)

  private val flowProducer = IncrementalUpdateFlowProducer(state)

  fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = flowProducer.getIncrementalUpdateFlow()
  override suspend fun setNodeChildren(
    parentId: Long,
    children: List<ProjectViewNodeModel>,
  ) {
    updateState(ProjectViewChildrenLoaded(parentId, children))
  }

  override suspend fun addNode(
    parentId: Long,
    index: Int,
    nodeModel: ProjectViewNodeModel,
  ) {
    updateState(ProjectViewNodeAdded(parentId, index, nodeModel))
  }

  override suspend fun updateNode(nodeModel: ProjectViewNodeModel) {
    updateState(ProjectViewNodeUpdated(nodeModel))
  }

  override suspend fun removeNodeChildren(parentId: Long) {
    updateState(ProjectViewChildrenRemoved(parentId))
  }

  override suspend fun removeNodeChild(parentId: Long, index: Int) {
    updateState(ProjectViewChildRemoved(parentId, index))
  }

  override suspend fun updateActionState(actionState: ProjectViewPaneSettingsStateDTO) {
    updateState(ProjectViewActionStateEvent(actionState))
  }

  override suspend fun clear() {
    updateState(ProjectViewClearStateEvent)
  }

  private suspend fun updateState(update: ProjectViewPaneStateEvent) {
    flowProducer.handleUpdate(update)
  }
}

private val LOG = logger<ProjectViewPaneStateBuilder>()
