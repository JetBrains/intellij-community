// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview

internal val BackendProjectViewPaneProviderEP = ExtensionPointName.create<ProjectViewPaneProvider>("com.intellij.project.view.pane.model.backend")

@Service(Service.Level.PROJECT)
internal class BackendProjectViewPaneService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : ProjectViewPaneService(project, coroutineScope, { BackendProjectViewPaneProviderEP.extensionList }, "BackendProjectViewPaneService") {
  companion object {
    fun getInstance(project: Project): BackendProjectViewPaneService = project.service()
  }
}
