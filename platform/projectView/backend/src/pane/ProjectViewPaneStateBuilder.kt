// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.projectView.actions.ProjectViewActionState
import com.intellij.platform.projectView.pane.ProjectViewActionStateEvent
import com.intellij.platform.projectView.pane.ProjectViewChildRemoved
import com.intellij.platform.projectView.pane.ProjectViewChildrenLoaded
import com.intellij.platform.projectView.pane.ProjectViewChildrenRemoved
import com.intellij.platform.projectView.pane.ProjectViewNodeAdded
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewNodeUpdated
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.SuperRootModel
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
    private var actionState: ProjectViewActionState? = null
    private val superRoot = Node(SuperRootModel)
    private val nodeById = hashMapOf<Long, Node>().also { it[SUPER_ROOT_ID] = superRoot }
    
    override suspend fun applyUpdate(update: ProjectViewPaneStateEvent): ProjectViewPaneStateEvent? {
      LOG.debug("Handling update: $update")
      when (update) {
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
      return update
    }

    override suspend fun takeSnapshot(): List<ProjectViewPaneStateEvent> {
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
  }

  private data class Node(var model: ProjectViewNodeModel, var children: MutableList<Node>? = null)

  private val flowProducer = IncrementalUpdateFlowProducer(state)

  override fun getStateFlow(): Flow<ProjectViewPaneStateEvent> = flowProducer.getIncrementalUpdateFlow()

  override suspend fun updateState(update: ProjectViewPaneStateEvent) {
    flowProducer.handleUpdate(update)
  }
}

private val LOG = logger<ProjectViewPaneStateBuilder>()
