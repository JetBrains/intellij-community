// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FrontendProjectViewPaneAggregatorImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : FrontendProjectViewPaneAggregator {

  private fun frontendService(): FrontendProjectViewPaneService = FrontendProjectViewPaneService.getInstance(project)

  private suspend fun backendService(): ProjectViewRpc = ProjectViewRpc.getInstance()
  
  private val paneDescriptorsDeferred: Deferred<Collection<AggregatedDescriptor>> = coroutineScope.async { 
    // TODO: for Light, we need to be able to transition for specific panes with matching IDs from the front to the back
    // In other words, for now it's just "backend wins," but we need "backend replaces frontend when it becomes available."
    (frontendService().getPaneDescriptors().associate { 
      it.id to AggregatedDescriptor(it, isFrontend = true)
    } + backendService().getPaneDescriptors(project.projectId()).associate { 
      it.id to AggregatedDescriptor(it, isFrontend = false)
    }).values
  }

  private val frontendIdsDeferred: Deferred<Set<ProjectViewPaneId>> = coroutineScope.async { 
    paneDescriptorsDeferred.await().filter { it.isFrontend }.mapTo(hashSetOf()) { it.descriptor.id }
  }

  private suspend fun frontendPaneIds(): Set<ProjectViewPaneId> = frontendIdsDeferred.await()

  override suspend fun getPaneDescriptors(): List<ProjectViewPaneDescriptorImpl> {
    return paneDescriptorsDeferred.await().map { it.descriptor }
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return if (paneId in frontendPaneIds()) {
      frontendService().getPaneStateFlow(paneId)
    }
    else {
      backendService().getPaneStateFlow(project.projectId(), paneId).map { it.toEvent() }
    }
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return if (paneId in frontendPaneIds()) {
      frontendService().getPaneRequestChannel(paneId)
    }
    else {
      backendService().getPaneRequestChannel(project.projectId(), paneId)
    }
  }

  override suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice): ProjectViewNodePath? {
    return if (paneId in frontendPaneIds()) {
      frontendService().findNodeForOpenedFile(paneId, editorChoice)
    }
    else {
      backendService().findNodeForOpenedFile(project.projectId(), paneId, editorChoice)
    }
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
