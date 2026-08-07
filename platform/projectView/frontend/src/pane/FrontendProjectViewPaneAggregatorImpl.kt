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
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  
  private val paneDescriptors = MutableStateFlow<Collection<ProjectViewPaneDescriptorImpl>>(emptyList())
  
  private val isBackendLoaded = AtomicBoolean(false)

  init {
    coroutineScope.launch(CoroutineName("PV pane descriptors fetching")) {
      val frontendDescriptors = frontendService().getPaneDescriptors().associateBy { it.id }
      if (!AppMode.isMonolith()) { // in the monolith mode, the backend is immediately available, no point loading light panes
        paneDescriptors.value = frontendDescriptors.values
      }
      LOG.info("Loaded the frontend PV pane descriptors: ${frontendDescriptors.values.joinToString { it.id.idString }}")
      val backendDescriptors = backendService().getPaneDescriptors(project.projectId()).associateBy { it.id }
      LOG.info("Loaded the backend PV pane descriptors: ${backendDescriptors.values.joinToString { it.id.idString }}")
      paneDescriptors.value = (frontendDescriptors + backendDescriptors).values
      isBackendLoaded.store(true)
    }
  }

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return paneDescriptors.asStateFlow()
  }

  override suspend fun getPaneStateFlow(paneDescriptor: ProjectViewPaneDescriptorImpl): Flow<ProjectViewPaneStateEvent> {
    return if (paneDescriptor.isFrontend) {
      frontendService().getPaneStateFlow(paneDescriptor.id)
    }
    else {
      backendService().getPaneStateFlow(project.projectId(), paneDescriptor.id).map { it.toEvent() }
    }
  }

  override suspend fun getPaneRequestChannel(paneDescriptor: ProjectViewPaneDescriptorImpl): SendChannel<ProjectViewPaneRequest> {
    return if (paneDescriptor.isFrontend) {
      frontendService().getPaneRequestChannel(paneDescriptor.id)
    }
    else {
      backendService().getPaneRequestChannel(project.projectId(), paneDescriptor.id)
    }
  }

  override suspend fun findNodeForOpenedFile(paneDescriptor: ProjectViewPaneDescriptorImpl, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    return if (paneDescriptor.isFrontend) {
      frontendService().findNodeForOpenedFile(paneDescriptor.id, editorChoice, isInvokedManually)
    }
    else {
      backendService().findNodeForOpenedFile(project.projectId(), paneDescriptor.id, editorChoice, isInvokedManually)
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
