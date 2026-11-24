// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.ProjectView
import com.intellij.platform.projectView.ProjectViewPanesImpl
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the [com.intellij.platform.projectView.impl.BackendProjectView] of the backend in sync with [projectView].
 *
 * Sends the panes of [projectView] to the backend, and selects the panes that the backend requests.
 * Runs until canceled.
 */
internal suspend fun syncPaneSelectionWithBackend(project: Project, projectView: ProjectView) {
  val projectId = project.projectId()
  coroutineScope {
    launch(CoroutineName("PV panes to the backend")) {
      durable {
        val panesChannel = ProjectViewRpc.awaitConnectionAndGetInstance().getPanesChannel(projectId)
        try {
          projectView.panes.collect { panes ->
            panesChannel.send(ProjectViewPanesImpl.of(panes))
          }
        }
        finally {
          panesChannel.close()
        }
      }
    }
    launch(CoroutineName("PV pane selection requests from the backend")) {
      durable {
        val rpc = ProjectViewRpc.awaitConnectionAndGetInstance()
        // A new request or a withdrawn one cancels the selection of the previous request.
        rpc.getSelectPaneRequestFlow(projectId).collectLatest { request ->
          if (request != null) {
            val isSelected = projectView.selectPane(request.paneId)
            LOG.debug { "The backend request $request is complete, isSelected = $isSelected" }
            rpc.completeSelectPaneRequest(projectId, request.requestId, isSelected)
          }
        }
      }
    }
  }
}

private val LOG = fileLogger()
