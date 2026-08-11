// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.structureView.impl.dto.NodeProviderNodesDto
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

internal class StructureUiModelImpl(private val dtoId: StructureViewDtoId) {
  @all:RequiresEdt
  var dto: StructureViewModelDto? = null
    private set

  internal val mutableUpdatePendingFlow: MutableStateFlow<Boolean> = MutableStateFlow(true)

  private val myModelListeners = ContainerUtil.createLockFreeCopyOnWriteList<StructureUiModelListener>()

  @get:RequiresEdt
  val rootElement: StructureViewNode = StructureViewNode()

  private val nodeById = HashMap<Int, StructureViewNode>().also {
    it[0] = rootElement
  }

  private val myEnabledActionNames = HashSet<String>()

  private var myActions: List<StructureTreeAction> = emptyList()

  internal val editorSelection = MutableStateFlow<StructureUiTreeElement?>(null)

  @all:RequiresEdt
  internal var rebuildTreeOnDeferredNodes = false

  val updatePendingFlow: StateFlow<Boolean>
    get() = mutableUpdatePendingFlow

  @get:RequiresEdt
  val smartExpand: Boolean
    get() = dto?.smartExpand ?: false

  @get:RequiresEdt
  val minimumAutoExpandDepth: Int
    get() = dto?.minimumAutoExpandDepth ?: 2

  @RequiresEdt
  fun initActions(modelDto: StructureViewModelDto) {
    dto = modelDto

    // Convert DTOs to impl classes
    myActions = modelDto.actions.map { it.toImpl() }

    myEnabledActionNames.addAll(myActions.filter {
      it.isReverted != it.isEnabledByDefault
    }.map { it.name })
    logger.trace { "StructureUiModelImpl[$dtoId]: initialized actions; enabledActions=${myEnabledActionNames}" }
  }

  @RequiresEdt
  fun getActions(): Collection<StructureTreeAction> = myActions

  @RequiresEdt
  fun isActionEnabled(action: StructureTreeAction): Boolean {
    return action.name in myEnabledActionNames
  }

  /**
   * Applies the new enabled state of [action] to this model.
   *
   * Returns `false` if the action already had that state, in which case the caller must not notify the backend.
   */
  @RequiresEdt
  fun setActionEnabled(action: StructureTreeAction, isEnabled: Boolean): Boolean {
    val affectiveIsEnabled = isEnabled != action.isReverted

    if (affectiveIsEnabled == isActionEnabled(action)) return false

    if (affectiveIsEnabled) {
      myEnabledActionNames.add(action.name)
    }
    else {
      myEnabledActionNames.remove(action.name)
    }

    if (action is NodeProviderTreeAction) {
      // If enabling an incomplete node provider, mark as pending and request tree rebuild when nodes arrive
      if (affectiveIsEnabled && !action.nodesLoaded) {
        mutableUpdatePendingFlow.value = true
        rebuildTreeOnDeferredNodes = true
      }
    }
    else if (action !is FilterTreeAction) {
      mutableUpdatePendingFlow.value = true
    }

    return true
  }

  @RequiresEdt
  fun applyNodesModel(
    rootDto: StructureViewTreeElementDto,
    nodeProviders: List<NodeProviderNodesDto>,
    nodes: List<StructureViewTreeElementDto>,
    editorSelectionId: Int?,
  ) {
    val applyStartTime = System.nanoTime()
    var selectionElement: StructureUiTreeElement? = null
    val reachableNodeIds = HashSet<Int>()

    rootElement.update(rootDto)
    reachableNodeIds.add(rootElement.id)
    if (editorSelectionId == rootElement.id) {
      selectionElement = rootElement
    }

    // Reuse nodes by id, but rebuild the backend-owned child lists from the latest DTO snapshot. FileStructurePopup separately
    // rebuilds visibleChildren from these sourceChildren when actions, sorting, or speed search change.
    for (node in nodeById.values) {
      node.sourceChildren.clear()
      node.parentNode = null
    }

    for (nodeDto in nodes) {
      val node = getOrCreateNode(nodeDto, reachableNodeIds)
      val parent = nodeById[nodeDto.parentId] ?: run {
        logger.error("No parent for ${node.id} or it's not a backend one")
        continue
      }
      node.parentNode = parent
      if (nodeDto.id == editorSelectionId) selectionElement = node
      parent.sourceChildren.add(node)
    }

    for (providerDto in nodeProviders) {
      val provider = myActions.find { it.name == providerDto.providerName } as? NodeProviderTreeAction
      if (provider == null) {
        logger.warn("No provider found for name: ${providerDto.providerName}")
        continue
      }

      val (selection, nodes) = convertNodesForProvider(editorSelectionId, providerDto.nodes, reachableNodeIds)

      if (selection != null) selectionElement = selection

      provider.setNodesByParentId(nodes)
    }

    nodeById.entries.removeIf { (id, _) ->
      id !in reachableNodeIds
    }

    editorSelection.value = selectionElement
    logger.trace {
      "StructureUiModelImpl[$dtoId]: applyNodesModel completed in ${(System.nanoTime() - applyStartTime).asTraceDuration()}; " +
      "nodes=${nodes.size}, providers=${nodeProviders.size}, providerNodes=${nodeProviders.sumOf { it.nodes.size }}, selection=${selectionElement?.id}"
    }
  }

  fun addListener(listener: StructureUiModelListener) {
    myModelListeners.add(listener)
  }

  fun clearListeners() {
    myModelListeners.clear()
  }

  @RequiresEdt
  fun fireTreeChanged() {
    myModelListeners.forEach { it.onTreeChanged() }
  }

  @RequiresEdt
  fun fireActionsChanged() {
    myModelListeners.forEach { it.onActionsChanged() }
  }

  private fun getOrCreateNode(nodeDto: StructureViewTreeElementDto, reachableNodeIds: MutableSet<Int>): StructureViewNode {
    reachableNodeIds.add(nodeDto.id)
    return nodeById.getOrPut(nodeDto.id) { StructureViewNode() }.also {
      it.update(nodeDto)
    }
  }

  private fun convertNodesForProvider(
    editorSelectionId: Int?,
    nodesDto: List<StructureViewTreeElementDto>,
    reachableNodeIds: MutableSet<Int>,
  ): Pair<StructureViewNode?, Map<Int, List<StructureViewNode>>> {
    val providerNodeMap = hashMapOf<Int, StructureViewNode>()
    val nodesByParentId = hashMapOf<Int, MutableList<StructureViewNode>>()
    var selectionElement: StructureViewNode? = null

    for (nodeDto in nodesDto) {
      val parent = providerNodeMap[nodeDto.parentId]
      val node = getOrCreateNode(nodeDto, reachableNodeIds)
      providerNodeMap[nodeDto.id] = node
      if (nodeDto.id == editorSelectionId) selectionElement = node

      if (parent == null) {
        node.parentNode = nodeById[nodeDto.parentId]
        nodesByParentId.getOrPut(nodeDto.parentId) { mutableListOf() }.add(node)
      }
      else {
        node.parentNode = parent
        parent.sourceChildren.add(node)
      }
    }
    return selectionElement to nodesByParentId
  }

  companion object {
    private val logger = logger<StructureUiModelImpl>()
  }
}

internal fun Long.asTraceDuration(): String {
  return "${TimeUnit.NANOSECONDS.toMillis(this)} ms"
}
