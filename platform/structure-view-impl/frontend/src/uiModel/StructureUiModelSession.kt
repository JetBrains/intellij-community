// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.ide.rpc.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.structureView.impl.StructureTreeApi
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import com.intellij.platform.structureView.impl.dto.TreeNodesDto
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the coroutine scope, the backend model handle and every [StructureTreeApi] call, and drives the EDT-confined
 * [StructureUiModelImpl] that holds the actual UI state.
 */
internal class StructureUiModelSession : StructureUiModel {
  private val dtoId: StructureViewDtoId

  private val cs: CoroutineScope

  private val model: StructureUiModelImpl

  /**
   * Constructor that fetches DTO via RPC (for monolith mode or when called from frontend).
   */
  constructor(fileEditor: FileEditor, file: VirtualFile, project: Project) {
    dtoId = StructureViewDtoId(nextId.getAndIncrement())
    cs = StructureViewScopeHolder.getInstance(project).cs.childScope("scope for ${file.name} structure view",
                                                                    Dispatchers.UI + ModalityState.any().asContextElement())
    model = StructureUiModelImpl(dtoId)
    initializeWithRpcModel(fileEditor, file, project)
  }

  /**
   * Constructor for popup flow with model pre-created in backend.
   */
  constructor(file: VirtualFile?, project: Project, modelId: StructureViewDtoId, modelDto: StructureViewModelDto) {
    dtoId = modelId
    cs = StructureViewScopeHolder.getInstance(project).cs.childScope("scope for ${file?.name} structure view",
                                                                    Dispatchers.UI + ModalityState.any().asContextElement())
    model = StructureUiModelImpl(dtoId)
    initializeWithProvidedModel(project, modelDto)
  }

  private fun initializeWithRpcModel(fileEditor: FileEditor, file: VirtualFile, project: Project) {
    // the service scope is used to make sure the disposal request to the backend is sent after the dto is received
    StructureViewScopeHolder.getInstance(project).cs.launch {
      val dto = durable {
        StructureTreeApi.getInstance().createStructureViewModel(dtoId, fileEditor.rpcId(), file.rpcId(), project.projectId())
      }

      if (dto == null) {
        logger.warn("No structure view model for $file")
        cs.launch {
          model.fireTreeChanged()
          model.mutableUpdatePendingFlow.value = false
        }
        return@launch
      }

      registerModelDisposal(project)
      cs.launch {
        initializeWithModel(dto)
      }
    }
  }

  private fun initializeWithProvidedModel(project: Project, modelDto: StructureViewModelDto) {
    registerModelDisposal(project)
    cs.launch {
      initializeWithModel(modelDto)
    }
  }

  private fun registerModelDisposal(project: Project) {
    // capture the service scope eagerly: the completion handler may run during project disposal,
    // when the service is no longer accessible (see ContainerDisposedException)
    val serviceScope = StructureViewScopeHolder.getInstance(project).cs
    cs.coroutineContext.job.invokeOnCompletion {
      serviceScope.launch {
        StructureTreeApi.callDisposeModel(dtoId)
      }
    }
  }

  private suspend fun initializeWithModel(modelDto: StructureViewModelDto) {
    model.initActions(modelDto)

    modelDto.nodes.toFlow().collect { nodesUpdate ->
      if (nodesUpdate == null) {
        return@collect
      }

      val updateStartTime = System.nanoTime()
      logger.trace { "StructureUiModelImpl[$dtoId]: nodes update received" }

      model.applyNodesModel(modelDto.rootNode,
                            nodesUpdate.nodeProviders,
                            nodesUpdate.nodes,
                            nodesUpdate.editorSelectionId)
      logger.trace {
        "StructureUiModelImpl[$dtoId]: applied nodes update in ${(System.nanoTime() - updateStartTime).asTraceDuration()}"
      }

      val uiNotifyStartTime = System.nanoTime()
      model.fireActionsChanged()
      model.fireTreeChanged()
      model.mutableUpdatePendingFlow.value = false
      logger.trace {
        "StructureUiModelImpl[$dtoId]: notified listeners for nodes update in ${(System.nanoTime() - uiNotifyStartTime).asTraceDuration()}"
      }

      handleDeferredNodeProviders(modelDto, nodesUpdate, updateStartTime)
    }
  }

  private suspend fun handleDeferredNodeProviders(
    modelDto: StructureViewModelDto,
    nodesUpdate: TreeNodesDto,
    updateStartTime: Long,
  ) {
    val deferredAwaitStartTime = System.nanoTime()
    val deferredNodes = try {
      nodesUpdate.deferredProviderNodes.await()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      model.rebuildTreeOnDeferredNodes = false
      model.mutableUpdatePendingFlow.value = false
      logger.error("Error computing provider nodes", e)
      return
    }
    logger.trace {
      "StructureUiModelImpl[$dtoId]: deferred provider await for nodes update completed in " +
      "${(System.nanoTime() - deferredAwaitStartTime).asTraceDuration()}; " +
      "deferredProviders=${deferredNodes?.nodeProviders?.size ?: 0}, "
    }

    if (deferredNodes != null) {
      val deferredApplyStartTime = System.nanoTime()
      model.applyNodesModel(modelDto.rootNode,
                            deferredNodes.nodeProviders,
                            deferredNodes.nodes,
                            nodesUpdate.editorSelectionId)
      logger.trace {
        "StructureUiModelImpl[$dtoId]: applied deferred provider nodes for update in " +
        (System.nanoTime() - deferredApplyStartTime).asTraceDuration()
      }
    }

    // If an incomplete node provider was enabled while waiting for deferred nodes, rebuild tree now.
    if (model.rebuildTreeOnDeferredNodes) {
      if (deferredNodes == null || deferredNodes.nodeProviders.isEmpty()) {
        logger.error("Deferred provider nodes list is empty, but rebuildTreeOnDeferredNodes is true")
      }
      model.rebuildTreeOnDeferredNodes = false
      model.fireTreeChanged()
      model.mutableUpdatePendingFlow.value = false
    }
    logger.trace {
      "StructureUiModelImpl[$dtoId]: nodes update completed in ${(System.nanoTime() - updateStartTime).asTraceDuration()}"
    }
  }

  override val dto: StructureViewModelDto?
    get() = model.dto

  override val rootElement: StructureUiTreeElement
    get() = model.rootElement

  override val smartExpand: Boolean
    get() = model.smartExpand

  override val minimumAutoExpandDepth: Int
    get() = model.minimumAutoExpandDepth

  override val editorSelection: StateFlow<StructureUiTreeElement?>
    get() = model.editorSelection

  override fun isActionEnabled(action: StructureTreeAction): Boolean = model.isActionEnabled(action)

  override fun getActions(): Collection<StructureTreeAction> = model.getActions()

  override fun setActionEnabled(action: StructureTreeAction, isEnabled: Boolean, isAutoClicked: Boolean) {
    if (!model.setActionEnabled(action, isEnabled)) return

    cs.launch {
      val id = dtoId
      StructureTreeApi.getInstance().setTreeActionState(id, action.name, isEnabled, isAutoClicked)
    }
  }

  override fun navigateTo(element: StructureUiTreeElement?): CompletableFuture<Boolean> {
    val deferred = CompletableFuture<Boolean>()

    if (element == null) {
      deferred.complete(false)
      return deferred
    }

    val elementId = element.id
    val modelId = dtoId

    cs.launch {
      val succeeded = StructureTreeApi.getInstance().navigateToElement(modelId, elementId)
      if (succeeded) {
        deferred.complete(true)
      }
      else {
        deferred.complete(false)
      }
    }.invokeOnCompletion {
      if (it != null) {
        deferred.completeExceptionally(it)
      }
    }

    return deferred
  }

  @TestOnly
  override suspend fun getNewSelection(): Int? {
    return StructureTreeApi.getInstance().getNewSelection(dtoId)
  }

  override fun getUpdatePendingFlow(): StateFlow<Boolean> = model.updatePendingFlow

  override fun addListener(listener: StructureUiModelListener) {
    model.addListener(listener)
  }

  override fun dispose() {
    cs.cancel()
    model.clearListeners()
  }

  companion object {
    private val nextId = AtomicInteger(1)
    private val logger = logger<StructureUiModelSession>()
  }
}
