// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import kotlinx.coroutines.CoroutineScope

/**
 * Providers of frontend-only Project View pane models: panes that produce their state on the frontend, without a backend.
 * The extension interface is the shared [ProjectViewPaneProvider] (pane models), deliberately distinct from
 * [FrontendProjectViewPaneProvider], which produces the Swing UI panes.
 */
internal val FrontendProjectViewPaneModelProviderEP = ExtensionPointName.create<ProjectViewPaneProvider>("com.intellij.project.view.pane.model.frontend")

@Service(Service.Level.PROJECT)
internal class FrontendProjectViewPaneService(
  project: Project,
  coroutineScope: CoroutineScope,
) : ProjectViewPaneService(project, coroutineScope, { FrontendProjectViewPaneModelProviderEP.extensionList }, "FrontendProjectViewPaneService") {
  companion object {
    fun getInstance(project: Project): FrontendProjectViewPaneService = project.service()
  }

  override val isFrontend: Boolean
    get() = true
}
