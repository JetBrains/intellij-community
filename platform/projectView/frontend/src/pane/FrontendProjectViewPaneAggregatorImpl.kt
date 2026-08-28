// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.pane

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class FrontendProjectViewPaneAggregatorImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : FrontendProjectViewPaneAggregator {

  private fun frontendService(): FrontendProjectViewPaneService = FrontendProjectViewPaneService.getInstance(project)

  private suspend fun backendService(): ProjectViewRpc = ProjectViewRpc.getInstance()
  
  // null means "not loaded yet", as opposed to "loaded, but there are no panes"
  private val frontendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)
  private val backendDescriptors = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneDescriptorImpl>?>(null)

  private val isBackendLoaded = AtomicBoolean(false)

  init {
    coroutineScope.launch(CoroutineName("PV frontend pane descriptors fetching")) {
      frontendService().getPaneDescriptorsFlow().collect { descriptors ->
        LOG.info("Loaded the frontend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        frontendDescriptors.value = descriptors.associateBy { it.id }
      }
    }
    coroutineScope.launch(CoroutineName("PV backend pane descriptors fetching")) {
      backendService().getPaneDescriptorsFlow(project.projectId()).collect { descriptors ->
        LOG.info("Loaded the backend PV pane descriptors: ${descriptors.joinToString { it.id.idString }}")
        backendDescriptors.value = descriptors.associateBy { it.id }
        isBackendLoaded.store(true)
      }
    }
  }

  override suspend fun getPaneDescriptorsFlow(): Flow<List<ProjectViewPaneDescriptorImpl>> {
    return combine(frontendDescriptors, backendDescriptors) { frontend, backend ->
      when {
        // The backend wins on ID collisions: that's how a light frontend pane is replaced by the real one.
        backend != null -> (frontend.orEmpty() + backend).values
        // In the monolith mode, the backend is immediately available, no point showing light panes.
        frontend != null && !AppMode.isMonolith() -> frontend.values
        else -> emptyList()
      }.sortedBy { it.order }
    }.distinctUntilChanged()
  }

  override suspend fun getPaneStateFlow(paneDescriptor: ProjectViewPaneDescriptorImpl): Flow<ProjectViewPaneStateEvent> {
    return when (paneDescriptor.kind) {
      ProjectViewPaneKind.LIGHT -> {
        frontendService().getPaneStateFlow(paneDescriptor.id)
      }
      ProjectViewPaneKind.BACKEND -> {
        backendService().getPaneStateFlow(project.projectId(), paneDescriptor.id).map { it.toEvent() }
      }
      ProjectViewPaneKind.UI_ONLY -> {
        TODO("Not implemented yet")
      }
    }
  }

  override suspend fun getPaneRequestChannel(paneDescriptor: ProjectViewPaneDescriptorImpl): SendChannel<ProjectViewPaneRequest> {
    return when (paneDescriptor.kind) {
      ProjectViewPaneKind.LIGHT -> {
        frontendService().getPaneRequestChannel(paneDescriptor.id)
      }
      ProjectViewPaneKind.BACKEND -> {
        backendService().getPaneRequestChannel(project.projectId(), paneDescriptor.id)
      }
      ProjectViewPaneKind.UI_ONLY -> {
        TODO("Not implemented yet")
      }
    }
  }

  override suspend fun findNodeForOpenedFile(paneDescriptor: ProjectViewPaneDescriptorImpl, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    return when (paneDescriptor.kind) {
      ProjectViewPaneKind.LIGHT -> {
        frontendService().findNodeForOpenedFile(paneDescriptor.id, editorChoice, isInvokedManually)
      }
      ProjectViewPaneKind.BACKEND -> {
        backendService().findNodeForOpenedFile(project.projectId(), paneDescriptor.id, editorChoice, isInvokedManually)
      }
      ProjectViewPaneKind.UI_ONLY -> {
        TODO("Not implemented yet")
      }
    }
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequestDTO): ProjectViewNodePath? {
    return if (isBackendLoaded.load()) {
      backendService().findNodeForSelectIn(project.projectId(), selectInRequest)
    }
    else {
      frontendService().findNodeForSelectIn(selectInRequest)
    }
  }
}

private val LOG = logger<FrontendProjectViewPaneAggregatorImpl>()
