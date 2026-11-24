// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Gives access to the panes of the Project View tool window.
 *
 * It works in the monolith and on both sides of remote development.
 * On the backend, it mirrors the state of the frontend through RPC.
 *
 * It works only with the split Project View.
 * When the split Project View is off, [panes] has no panes and [selectPane] returns `false`.
 *
 * Get the instance with [getInstance]. The instance is thread-safe.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectView {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectView = project.service()
  }

  /**
   * The panes that the tool window shows and its current pane.
   *
   * The value has no panes until the tool window adds its first pane.
   * On the backend, the value has no panes until a frontend connects.
   * After the frontend disconnects, the value keeps the last state of the frontend.
   */
  val panes: StateFlow<ProjectViewPanes>

  /**
   * Makes the pane [paneId] the current pane of the tool window.
   * The call does not show or activate the tool window.
   *
   * If the tool window has not added its first panes yet, the call waits for them.
   * On the backend, the call also waits for a frontend to connect.
   * Cancel the call to stop the wait.
   *
   * Another selection request that comes before the call selects the pane supersedes the call.
   * Such a request is another call of this function, or a pane selection in the tool window by the user or by other code.
   * In remote development, the frontend selects the pane, and a request of the backend comes to the frontend after a delay.
   *
   * @return `true` if the pane became the current pane.
   * `false` if another request superseded the call, the tool window has no such pane, the tool window rejected the change,
   * or the split Project View is off.
   */
  suspend fun selectPane(paneId: ProjectViewPaneId): Boolean
}
