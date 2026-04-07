// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequest
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Service(Service.Level.PROJECT)
internal class BackendProjectViewPaneService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): BackendProjectViewPaneService = project.service()
    suspend fun getInstanceSuspend(project: Project): BackendProjectViewPaneService = project.serviceAsync()
  }
  
  private val panes = AtomicReference<Map<ProjectViewPaneId, BackendProjectViewPane>?>(null)
  
  private val panesDeferred = coroutineScope.async(CoroutineName("BackendProjectViewPaneService: pane computation")) {
    val result = hashMapOf<ProjectViewPaneId, BackendProjectViewPane>()
    for (provider in BackendProjectViewPaneProviderEP.extensionList) {
      for (pane in provider.createPanes(project)) {
        result[pane.id] = pane
      }
    }
    panes.store(result)
    result
  }

  init {
    coroutineScope.launch(CoroutineName("BackendProjectViewPaneService: pane management")) {
      val panes = panesDeferred.await()
      supervisorScope {
        for (pane in panes.values) {
          launch(CoroutineName("BackendProjectViewPaneService: pane ${pane.id}")) {
            pane.manage()
          }
        }
      }
    }
  }

  suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return panesDeferred.await()[paneId]?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  suspend fun getPaneDescriptors(): List<ProjectViewPaneDescriptor> {
    return panesDeferred.await().values.toList().map { it.descriptor }
  }

  suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return panesDeferred.await()[paneId]?.getPaneStateFlow() ?: emptyFlow()
  }
  
  fun getPane(paneId: ProjectViewPaneId): BackendProjectViewPane? {
    return panes.load()?.get(paneId)
  }

  suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice): ProjectViewNodePath? {
    val panes = panes.load() ?: return null
    val pane = panes[paneId] ?: return null
    return pane.findNodeForEditor(editorChoice)
  }

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    val panes = panes.load() ?: return null
    val pane = panes.values.firstOrNull { pane ->
      pane.descriptor.selectInTargetDescriptors.any { selectInTargetDescriptor ->
        selectInTargetDescriptor.id == selectInRequest.targetId
      }
    } ?: return null
    return pane.findNodeForSelectIn(selectInRequest)
  }
}
