// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.ide.SelectInContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.BackendProjectViewSelectInChecker
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneServiceBase
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope

internal val BackendProjectViewPaneProviderEP = ExtensionPointName.create<ProjectViewPaneProvider>("com.intellij.project.view.pane.model.backend")

@Service(Service.Level.PROJECT)
internal class BackendProjectViewPaneService(
  project: Project,
  coroutineScope: CoroutineScope,
) : ProjectViewPaneServiceBase(project, coroutineScope, { BackendProjectViewPaneProviderEP.extensionList }, "BackendProjectViewPaneService") {
  companion object {
    fun getInstance(project: Project): BackendProjectViewPaneService = project.service()
  }

  override val isFrontend: Boolean
    get() = false
}

internal class BackendProjectViewSelectInCheckerImpl(private val project: Project) : BackendProjectViewSelectInChecker {
  @RequiresReadLock
  override fun canSelect(paneId: ProjectViewPaneId, context: SelectInContext): Boolean {
    return BackendProjectViewPaneService.getInstance(project).canSelect(paneId, context)
  }
}
