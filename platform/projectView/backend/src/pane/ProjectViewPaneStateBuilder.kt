// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.projectView.pane.*
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun projectViewPaneStateBuilder() : ProjectViewPaneStateBuilder = ProjectViewPaneStateBuilderImpl()

@ApiStatus.Internal
interface ProjectViewPaneStateBuilder {
  fun getStateFlow(): Flow<ProjectViewPaneStateEvent>
  suspend fun updateState(update: ProjectViewPaneStateEvent)
}

private class ProjectViewPaneStateBuilderImpl : ProjectViewPaneStateBuilder {
  private val state = object : MutableStateWithIncrementalUpdates<ProjectViewPaneStateEvent> {
    private val superRoot = Node(
      SuperRootModel,
      mutableListOf()
    )
    private val nodeById = hashMapOf<Long, Node>().also { it[SUPER_ROOT_ID] = superRoot }
    
    override suspend fun applyUpdate(update: ProjectViewPaneStateEvent): ProjectViewPaneStateEvent? {
      LOG.debug("Handling update: $update")
      when (update) {
        is ProjectViewChildrenLoaded -> {
          val parent = nodeById[update.parentId] ?: return null
          val children = update.children.map { Node(it, mutableListOf()) }
          parent.children.addAll(children)
          for (child in children) {
            nodeById[child.model.id] = child
          }
        }
        is ProjectViewNodeAdded -> {
          val parent = nodeById[update.parentId] ?: return null
          val newNode = Node(update.model, mutableListOf())
          parent.children.add(update.index, newNode)
          nodeById[newNode.model.id] = newNode
        }
        is ProjectViewChildRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          val removedNode = parent.children.removeAt(update.index)
          nodeById.remove(removedNode.model.id)
        }
        is ProjectViewChildrenRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          for (child in parent.children) {
            nodeById.remove(child.model.id)
          }
          parent.children.clear()
        }
        is ProjectViewNodeUpdated -> {
          val node = nodeById[update.model.id] ?: return null
          node.model = update.model
        }
      }
      LOG.debug("Handled update: $update")
      return update
    }

    override suspend fun takeSnapshot(): List<ProjectViewPaneStateEvent> {
      val result = ArrayList<ProjectViewPaneStateEvent>(nodeById.size)
      val bfsQueue = ArrayDeque<ProjectViewNodeAdded>()
      bfsQueue.addLast(ProjectViewNodeAdded(-1L, 0, SuperRootModel))
      while (true) {
        val next = bfsQueue.removeFirstOrNull() ?: break
        if (next.model.id != SUPER_ROOT_ID) result.add(next)
        for ((index, child) in nodeById.getValue(next.model.id).children.withIndex()) {
          bfsQueue.addLast(ProjectViewNodeAdded(next.model.id, index, child.model))
        }
      }
      return result
    }
  }

  private data class Node(var model: ProjectViewNodeModel, val children: MutableList<Node>)

  private val flowProducer = IncrementalUpdateFlowProducer(state)

  override fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = flowProducer.getIncrementalUpdateFlow()

  override suspend fun updateState(update: ProjectViewPaneStateEvent) {
    flowProducer.handleUpdate(update)
  }
}

private val LOG = logger<ProjectViewPaneStateBuilder>()
