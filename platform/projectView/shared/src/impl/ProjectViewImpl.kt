// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.ProjectView
import com.intellij.platform.projectView.ProjectViewPanes
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * The [ProjectView] of the backend in remote development.
 *
 * It mirrors the state of the frontend through [com.intellij.platform.projectView.rpc.ProjectViewRpc].
 */
@ApiStatus.Internal
interface BackendProjectView : ProjectView

internal class ProjectViewImpl(private val project: Project) : ProjectView {
  /**
   * The frontend module registers [ProjectViewToolWindowService] in the monolith, in the light mode and on the frontend.
   * Only the backend of remote development has no tool window service, and it uses [BackendProjectView] instead.
   */
  private val delegate: ProjectView by lazy {
    project.serviceOrNull<ProjectViewToolWindowService>() ?: project.service<BackendProjectView>()
  }

  override val panes: StateFlow<ProjectViewPanes>
    get() = delegate.panes

  override suspend fun selectPane(paneId: ProjectViewPaneId): Boolean = delegate.selectPane(paneId)
}
