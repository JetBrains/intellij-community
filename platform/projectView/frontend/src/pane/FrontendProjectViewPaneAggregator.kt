// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.pane

import com.intellij.idea.AppMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.frontend.impl.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Service(Service.Level.PROJECT)
internal class FrontendProjectViewPaneAggregator(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): FrontendProjectViewPaneAggregator = project.service()
  }

  private val backendServiceDeferred = coroutineScope.async(CoroutineName("Waiting for the Project View backend")) {
    BackendDelegatingProjectViewPaneService(project, ProjectViewRpc.getInstance())
  }
  
  // null means "not loaded yet", as opposed to "loaded, but there are no panes"
  private val uiDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)
  private val frontendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)
  private val backendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)

  private val isBackendLoaded = AtomicBoolean(false)

  init {
    coroutineScope.launch(CoroutineName("PV UI pane descriptors fetching")) {
      pureUiService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the UI pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        uiDescriptors.value = descriptors.associateBy { it.id }
      }
    }
    coroutineScope.launch(CoroutineName("PV frontend pane descriptors fetching")) {
      frontendService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the frontend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        frontendDescriptors.value = descriptors.associateBy { it.id }
      }
    }
    coroutineScope.launch(CoroutineName("PV backend pane descriptors fetching")) {
      backendService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the backend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        backendDescriptors.value = descriptors.associateBy { it.id }
        isBackendLoaded.store(true)
      }
    }
  }

  private fun frontendService(): ProjectViewPaneService = FrontendProjectViewPaneService.getInstance(project)

  private suspend fun backendService(): ProjectViewPaneService = backendServiceDeferred.await()

  private fun pureUiService(): PureUiProjectViewPaneService = PureUiProjectViewPaneService.getInstance(project)
  
  private suspend fun paneService(descriptor: ProjectViewPaneDescriptorImpl): ProjectViewPaneService {
    return when (descriptor.kind) {
      ProjectViewPaneKind.BACKEND -> backendService()
      ProjectViewPaneKind.LIGHT -> frontendService()
      ProjectViewPaneKind.UI_ONLY -> pureUiService()
    }
  }

  fun getPaneDescriptorsFlow(): Flow<List<ProjectViewPaneDescriptorImpl>> {
    return combine(uiDescriptors, frontendDescriptors, backendDescriptors) { ui, frontend, backend ->
      val pureUiPaneDescriptors = ui.orEmpty()
      val fullPaneDescriptors = when {
        // The backend wins on ID collisions: that's how a light frontend pane is replaced by the real one.
        backend != null -> (frontend.orEmpty() + backend).values
        // In the monolith mode, the backend is immediately available, no point showing light panes.
        frontend != null && !AppMode.isMonolith() -> frontend.values
        else -> emptyList()
      }
      val result = mutableListOf<ProjectViewPaneDescriptorImpl>()
      for (pureUiDescriptor in pureUiPaneDescriptors.values) {
        result += pureUiDescriptor
      }
      // The choice between a pure UI pane and a full pane is arbitrary, it's an error either way.
      // If we end up in this situation, it's most likely the legacy pane compatibility layer picked something.
      // So our best guess is to pick the pure UI one instead.
      for (fullDescriptor in fullPaneDescriptors) {
        if (fullDescriptor.id in pureUiPaneDescriptors) {
          LOG.warn(
            "Duplicate pane ID ${fullDescriptor.id}: " +
            "full = $fullDescriptor, " +
            "pure UI = ${pureUiPaneDescriptors[fullDescriptor.id]}"
          )
          continue
        }
        result += fullDescriptor
      }
      result.sortedBy { it.order }
    }.distinctUntilChanged()
  }

  @RequiresEdt
  fun createPane(descriptor: ProjectViewPaneDescriptorImpl): FrontendProjectViewPane? {
    return when (descriptor.kind) {
      ProjectViewPaneKind.BACKEND -> TreeBasedFrontendProjectViewPane(project, descriptor)
      ProjectViewPaneKind.LIGHT -> TreeBasedFrontendProjectViewPane(project, descriptor)
      ProjectViewPaneKind.UI_ONLY -> pureUiService().createPane(descriptor)
    }
  }

  suspend fun getPaneStateFlow(paneDescriptor: ProjectViewPaneDescriptorImpl): Flow<ProjectViewPaneStateEvent>? {
    return paneService(paneDescriptor).getPaneStateFlow(paneDescriptor.id)
  }

  suspend fun getPaneRequestChannel(paneDescriptor: ProjectViewPaneDescriptorImpl): SendChannel<ProjectViewPaneRequest>? {
    return paneService(paneDescriptor).getPaneRequestChannel(paneDescriptor.id)
  }

  suspend fun findNodeForOpenedFile(paneDescriptor: ProjectViewPaneDescriptorImpl, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    return paneService(paneDescriptor).findNodeForOpenedFile(paneDescriptor.id, editorChoice, isInvokedManually)
  }

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequestDTO): ProjectViewNodePath? {
    return if (isBackendLoaded.load()) {
      backendService().findNodeForSelectIn(selectInRequest)
    }
    else {
      frontendService().findNodeForSelectIn(selectInRequest)
    }
  }
}

private class BackendDelegatingProjectViewPaneService(
  private val project: Project,
  private val rpc: ProjectViewRpc,
) : ProjectViewPaneService {

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return rpc.getPaneDescriptorsFlow(project.projectId()).map { dtos ->
      dtos.map { it.toDescriptor() }
    }
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent>? {
    return rpc.getPaneStateFlow(project.projectId(), paneId)?.map { it.toEvent() }
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>? {
    return rpc.getPaneRequestChannel(project.projectId(), paneId)
  }

  override suspend fun findNodeForOpenedFile(
    paneId: ProjectViewPaneId,
    editorChoice: EditorChoice,
    isInvokedManually: Boolean,
  ): ProjectViewNodePath? {
    return rpc.findNodeForOpenedFile(project.projectId(), paneId, editorChoice, isInvokedManually)
  }

  override suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath? {
    return rpc.findNodeForSelectIn(project.projectId(), selectInRequestDTO)
  }
}

private val LOG = logger<FrontendProjectViewPaneAggregator>()
