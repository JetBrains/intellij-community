// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * A thin frontend-side API that collects Project View panes from both the backend (over [com.intellij.platform.projectView.rpc.ProjectViewRpc])
 * and the frontend (directly from `FrontendProjectViewPaneService`, without RPC).
 *
 * Unlike the RPC, this API speaks in [ProjectViewPaneStateEvent] rather than the serializable
 * [ProjectViewPaneStateEventDTO]. Frontend-only panes never cross a serialization boundary, so events they produce keep their
 * `@Transient` (backend-only, in-process) properties. Backend panes still arrive as DTOs over RPC and are converted here.
 */
@ApiStatus.Internal
interface FrontendProjectViewPaneAggregator {
  suspend fun getPaneDescriptorsFlow(): Flow<List<ProjectViewPaneDescriptorImpl>>

  suspend fun getPaneStateFlow(paneDescriptor: ProjectViewPaneDescriptorImpl): Flow<ProjectViewPaneStateEvent>

  suspend fun getPaneRequestChannel(paneDescriptor: ProjectViewPaneDescriptorImpl): SendChannel<ProjectViewPaneRequest>

  suspend fun findNodeForOpenedFile(paneDescriptor: ProjectViewPaneDescriptorImpl, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath?

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequestDTO): ProjectViewNodePath?

  companion object {
    fun getInstance(project: Project): FrontendProjectViewPaneAggregator = project.service()
  }
}
