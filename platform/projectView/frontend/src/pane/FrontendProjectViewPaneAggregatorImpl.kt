// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
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

internal class FrontendProjectViewPaneAggregatorImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : FrontendProjectViewPaneAggregator {

  private fun frontendService(): FrontendProjectViewPaneService = FrontendProjectViewPaneService.getInstance(project)

  private suspend fun backendService(): ProjectViewRpc = ProjectViewRpc.getInstance()
  
  private val paneDescriptors = MutableStateFlow<Collection<AggregatedDescriptor>>(emptyList())
  
  init {
    coroutineScope.launch(CoroutineName("PV pane descriptors fetching")) {
      val frontendDescriptors = frontendService().getPaneDescriptors().associate {
        it.id to AggregatedDescriptor(it, isFrontend = true)
      }
      paneDescriptors.value = frontendDescriptors.values
      LOG.info("Loaded the frontend PV pane descriptors: ${frontendDescriptors.values.joinToString { it.descriptor.id.idString }}")
      val backendDescriptors = backendService().getPaneDescriptors(project.projectId()).associate {
        it.id to AggregatedDescriptor(it, isFrontend = false)
      }
      LOG.info("Loaded the backend PV pane descriptors: ${backendDescriptors.values.joinToString { it.descriptor.id.idString }}")
      paneDescriptors.value = (frontendDescriptors + backendDescriptors).values
    }
  }

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return paneDescriptors.asStateFlow().map { descriptors -> descriptors.map { it.descriptor } }
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return if (isFrontendPane(paneId)) {
      frontendService().getPaneStateFlow(paneId)
    }
    else {
      backendService().getPaneStateFlow(project.projectId(), paneId).map { it.toEvent() }
    }
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return if (isFrontendPane(paneId)) {
      frontendService().getPaneRequestChannel(paneId)
    }
    else {
      backendService().getPaneRequestChannel(project.projectId(), paneId)
    }
  }

  override suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice): ProjectViewNodePath? {
    return if (isFrontendPane(paneId)) {
      frontendService().findNodeForOpenedFile(paneId, editorChoice)
    }
    else {
      backendService().findNodeForOpenedFile(project.projectId(), paneId, editorChoice)
    }
  }

  private fun isFrontendPane(paneId: ProjectViewPaneId): Boolean {
    val descriptors = paneDescriptors.value.filter { it.descriptor.id == paneId }
    return descriptors.all { it.isFrontend } // when we have a mix, the backend wins
  }

  override suspend fun findNodeForSelectIn(selectInRequest: SelectInRequestDTO): ProjectViewNodePath? {
    // Select-In doesn't carry a pane id, so ask the frontend panes first (their live @Transient context survives in-process),
    // and fall back to the backend when no frontend pane owns the requested target.
    return frontendService().findNodeForSelectIn(selectInRequest)
           ?: backendService().findNodeForSelectIn(project.projectId(), selectInRequest)
  }
}

private data class AggregatedDescriptor(
  val descriptor: ProjectViewPaneDescriptorImpl,
  val isFrontend: Boolean,
)

private val LOG = logger<FrontendProjectViewPaneAggregatorImpl>()
