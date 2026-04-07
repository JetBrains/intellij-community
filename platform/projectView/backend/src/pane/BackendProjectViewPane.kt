// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequest
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

internal val BackendProjectViewPaneProviderEP = ExtensionPointName.create<BackendProjectViewPaneProvider>("com.intellij.project.view.pane.backend")

@ApiStatus.Internal
interface BackendProjectViewPaneProvider {
  fun createPanes(project: Project): List<BackendProjectViewPane>
}

@ApiStatus.Internal
interface BackendProjectViewPane {

  val descriptor: ProjectViewPaneDescriptor

  suspend fun manage()

  suspend fun getPaneStateFlow(): Flow<ProjectViewPaneStateEvent>

  fun getRequestChannel(): SendChannel<ProjectViewPaneRequest>

  fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot)

  suspend fun findNodeForEditor(editorChoice: EditorChoice): ProjectViewNodePath?

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath?
}

@get:ApiStatus.Internal
val BackendProjectViewPane.id: ProjectViewPaneId
  get() = descriptor.id
