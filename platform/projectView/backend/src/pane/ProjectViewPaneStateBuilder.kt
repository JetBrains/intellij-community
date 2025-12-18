// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.projectView.pane.*
import com.intellij.platform.util.coroutines.flow.IncrementalUpdateFlowProducer
import com.intellij.platform.util.coroutines.flow.MutableStateWithIncrementalUpdates
import com.intellij.ui.treeStructure.TreeNodePresentation
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
      SUPER_ROOT_ID,
      SuperRootPresentation,
      mutableListOf()
    )
    private val nodeById = hashMapOf<Long, Node>().also { it[SUPER_ROOT_ID] = superRoot }
    
    override suspend fun applyUpdate(update: ProjectViewPaneStateEvent): ProjectViewPaneStateEvent? {
      LOG.debug("Handling update: $update")
      when (update) {
        is ProjectViewNodeAdded -> {
          val parent = nodeById[update.parentId] ?: return null
          val newNode = Node(update.nodeId, update.presentation, mutableListOf())
          parent.children.add(update.index, newNode)
          nodeById[newNode.id] = newNode
        }
        is ProjectViewChildRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          val removedNode = parent.children.removeAt(update.index)
          nodeById.remove(removedNode.id)
        }
        is ProjectViewChildrenRemoved -> {
          val parent = nodeById[update.parentId] ?: return null
          for (child in parent.children) {
            nodeById.remove(child.id)
          }
          parent.children.clear()
        }
        is ProjectViewNodeUpdated -> {
          val node = nodeById[update.nodeId] ?: return null
          node.presentation = update.presentation
        }
      }
      LOG.debug("Handled update: $update")
      return update
    }

    override suspend fun takeSnapshot(): List<ProjectViewPaneStateEvent> {
      val result = ArrayList<ProjectViewPaneStateEvent>(nodeById.size)
      val bfsQueue = ArrayDeque<ProjectViewNodeAdded>()
      bfsQueue.addLast(ProjectViewNodeAdded(-1L, 0, SUPER_ROOT_ID, superRoot.presentation))
      while (true) {
        val next = bfsQueue.removeFirstOrNull() ?: break
        if (next.nodeId != SUPER_ROOT_ID) result.add(next)
        for ((index, child) in nodeById.getValue(next.nodeId).children.withIndex()) {
          bfsQueue.addLast(ProjectViewNodeAdded(next.nodeId, index, child.id, child.presentation))
        }
      }
      return result
    }
  }

  private data class Node(val id: Long, var presentation: TreeNodePresentation, val children: MutableList<Node>)

  private val flowProducer = IncrementalUpdateFlowProducer(state)

  override fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = flowProducer.getIncrementalUpdateFlow()

  override suspend fun updateState(update: ProjectViewPaneStateEvent) {
    flowProducer.handleUpdate(update)
  }
}

private val LOG = logger<ProjectViewPaneStateBuilder>()
