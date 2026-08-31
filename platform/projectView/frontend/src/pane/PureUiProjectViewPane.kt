// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilderImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PureUiProjectViewPaneProvider {
  fun describe(project: Project, builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor
  
  @RequiresEdt
  fun createPane(project: Project, descriptor: ProjectViewPaneDescriptor): PureUiProjectViewPane
}

@ApiStatus.Experimental
interface PureUiProjectViewPane : FrontendProjectViewPane

private val PURE_UI_EP = ExtensionPointName.create<PureUiProjectViewPaneProvider>("com.intellij.project.view.pane.pure.ui")

@Service(Service.Level.PROJECT)
internal class PureUiProjectViewPaneService(private val project: Project) : ProjectViewPaneService {
  companion object {
    fun getInstance(project: Project): PureUiProjectViewPaneService = project.service()
  }
  
  override suspend fun getPaneDescriptorsFlow(): Flow<List<ProjectViewPaneDescriptorImpl>> {
    return flowOf(PURE_UI_EP.extensionList.map { provider -> describe(provider) })
  }
  
  private fun describe(provider: PureUiProjectViewPaneProvider): ProjectViewPaneDescriptorImpl {
    val builder = ProjectViewPaneDescriptorBuilderImpl()
    builder.kind = ProjectViewPaneKind.UI_ONLY
    return provider.describe(project, builder) as ProjectViewPaneDescriptorImpl
  }
  
  fun createPane(descriptor: ProjectViewPaneDescriptorImpl): FrontendProjectViewPane {
    val result = PURE_UI_EP.computeSafeIfAny { provider ->
      val eachDescriptor = describe(provider)
      if (eachDescriptor == descriptor) {
        provider.createPane(project, descriptor)
      }
      else {
        null
      }
    }
    if (result == null) {
      throw IllegalArgumentException("The pane ${descriptor.id} was not found")
    }
    return result
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent>? {
    return null
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>? {
    return null
  }

  override suspend fun findNodeForOpenedFile(
    paneId: ProjectViewPaneId,
    editorChoice: EditorChoice,
    isInvokedManually: Boolean,
  ): ProjectViewNodePath? {
    return null
  }

  override suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath? {
    return null
  }
}
