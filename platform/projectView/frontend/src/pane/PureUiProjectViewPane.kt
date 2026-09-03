// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilderImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PureUiProjectViewPaneProvider {
  fun getPaneModelsFlow(project: Project): Flow<Collection<FrontendProjectViewPaneModel>>
}

private val PURE_UI_EP = ExtensionPointName.create<PureUiProjectViewPaneProvider>("com.intellij.project.view.pane.pure.ui")

@Service(Service.Level.PROJECT)
internal class PureUiProjectViewPaneService(
  private val project: Project,
  scope: CoroutineScope,
) : ProjectViewPaneService {
  companion object {
    fun getInstance(project: Project): PureUiProjectViewPaneService = project.service()
  }

  private val modelByDescriptor = MutableStateFlow<Map<ProjectViewPaneDescriptorImpl, FrontendProjectViewPaneModel>?>(null)

  init {
    scope.launch(CoroutineName("Fetch pure UI pane descriptors")) {
      val modelFlows = PURE_UI_EP.extensionList.mapNotNull {
        try {
          it.getPaneModelsFlow(project)
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.warn("The pure UI extension's getPaneModelsFlow() is broken, skipping: $it", e)
          null
        }
      }
      combine(modelFlows) { modelsByProvider -> modelsByProvider.flatMap { it } }.collect { models ->
        modelByDescriptor.value = models
          .mapNotNull { 
            try {
              describe(it) to it
            }
            catch (e: Throwable) {
              rethrowControlFlowException(e)
              LOG.warn("The pure UI extension's describe() is broken, skipping: $it", e)
              null
            }
          }
          .toMap()
      }
    }
  }

  private suspend fun describe(model: FrontendProjectViewPaneModel): ProjectViewPaneDescriptorImpl {
    val builder = ProjectViewPaneDescriptorBuilderImpl()
    builder.kind = ProjectViewPaneKind.UI_ONLY
    return model.describe(builder) as ProjectViewPaneDescriptorImpl
  }

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return modelByDescriptor.filterNotNull().map { it.keys }
  }

  fun createPane(descriptor: ProjectViewPaneDescriptorImpl): FrontendProjectViewPane? {
    return modelByDescriptor.value?.get(descriptor)?.createPane(descriptor)
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

private val LOG = logger<PureUiProjectViewPaneService>()
