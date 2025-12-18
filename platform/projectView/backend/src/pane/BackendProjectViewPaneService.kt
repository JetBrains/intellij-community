// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Service(Service.Level.PROJECT)
internal class BackendProjectViewPaneService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    suspend fun getInstanceSuspend(project: Project): BackendProjectViewPaneService = project.serviceAsync()
  }
  
  private val panes = coroutineScope.async(CoroutineName("BackendProjectViewPaneService: pane computation")) {
    val result = hashMapOf <ProjectViewPaneProviderId, Map<ProjectViewPaneId, BackendProjectViewPane>>()
    for (provider in BackendProjectViewPaneProviderEP.extensionList) {
      val panes = hashMapOf<ProjectViewPaneId, BackendProjectViewPane>()
      for (pane in provider.createPanes(project)) {
        panes[pane.id] = pane
      }
      result[provider.id] = panes
    }
    result
  }

  init {
    coroutineScope.launch(CoroutineName("BackendProjectViewPaneService: pane management")) {
      val panes = panes.await()
      supervisorScope {
        for (pane in panes.values.flatMap { it.values }) {
          if (pane.id != projectViewPaneId("ProjectPane")) continue
          launch(CoroutineName("BackendProjectViewPaneService: pane ${pane.id}")) { 
            pane.manage()
          }
        }
      }
    }
  }

  suspend fun getPaneRequestChannel(providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return panes.await()[providerId]?.get(paneId)?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  suspend fun getPaneIds(providerId: ProjectViewPaneProviderId): List<ProjectViewPaneId> {
    return panes.await()[providerId]?.keys?.toList() ?: emptyList()
  }

  suspend fun getPaneStateFlow(providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return panes.await()[providerId]?.get(paneId)?.getPaneStateFlow() ?: emptyFlow()
  }
}
