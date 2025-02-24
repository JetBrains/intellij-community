// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.codeInsight.navigation.LOG
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.Switcher.SwitcherPanel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModelUpdate
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import com.intellij.platform.recentFiles.shared.RecentFilesEvent.*
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

internal fun createAndShowNewSwitcher(onlyEditedFiles: Boolean?, event: AnActionEvent?, @Nls title: String, project: Project) {
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewSwitcherSuspend(onlyEditedFiles, event, title, project)
  }
}

@OptIn(FlowPreview::class)
@ApiStatus.Internal
suspend fun createAndShowNewSwitcherSuspend(onlyEditedFiles: Boolean?, event: AnActionEvent?, @Nls title: String, project: Project): SwitcherPanel {
  val serviceScope = RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope

  val uiUpdateScope = serviceScope.childScope("Switcher UI updates")
  val modelUpdateScope = serviceScope.childScope("Switcher backend requests")

  val dataModel = createReactiveDataModel(serviceScope, project)
  val remoteApi = FileSwitcherApi.getInstance()
  val parameters = SwitcherLaunchEventParameters(event?.inputEvent)

  modelUpdateScope.launch(start = CoroutineStart.UNDISPATCHED) {
    remoteApi.updateRecentFilesBackendState(createFilesSearchRequestRequest(true == onlyEditedFiles, !parameters.isEnabled, project))
  }
  // try waiting for the initial bunch of files to load before displaying the UI
  dataModel.awaitModelPopulation(durationMillis = Registry.intValue("switcher.preload.timeout.ms", 300).toLong())
  return withContext(Dispatchers.EDT) {
    SwitcherPanel(project = project,
                  title = title,
                  launchParameters = parameters,
                  onlyEditedFiles = onlyEditedFiles,
                  givenFilesModel = dataModel,
                  modelUpdateScope = modelUpdateScope,
                  uiUpdateScope = uiUpdateScope,
                  remoteApi = remoteApi)
  }
}

private suspend fun createReactiveDataModel(parentScope: CoroutineScope, project: Project): FlowBackedListModel<SwitcherVirtualFile> {
  val mappedEvents = FileSwitcherApi.getInstance()
    .getRecentFileEvents(project.projectId())
    .map { recentFilesUpdate -> convertRpcEventToFlowModelEvent(recentFilesUpdate) }
  val modelSubscriptionScope = parentScope.childScope("FlowBackedListModel events subscription")
  return FlowBackedListModel(modelSubscriptionScope, mappedEvents)
}

// FIXME: Unnecessary conversion, consider setting up KX serialisation so that generic type parameter's serializer is recognised
private fun convertRpcEventToFlowModelEvent(rpcEvent: RecentFilesEvent): FlowBackedListModelUpdate<SwitcherVirtualFile> {
  LOG.debug("Switcher convert rpc to model event: $rpcEvent")
  return when (rpcEvent) {
    is ItemsAdded -> FlowBackedListModelUpdate.ItemsAdded(rpcEvent.batch.map(::convertSwitcherDtoToViewModel))
    is ItemsRemoved -> FlowBackedListModelUpdate.ItemsRemoved(rpcEvent.batch.map(::convertSwitcherDtoToViewModel))
    is AllItemsRemoved -> FlowBackedListModelUpdate.AllItemsRemoved()
    is EndOfUpdates -> FlowBackedListModelUpdate.UpdateCompleted()
  }
}

private fun convertSwitcherDtoToViewModel(rpcDto: SwitcherRpcDto): SwitcherVirtualFile {
  return when (rpcDto) {
    is SwitcherRpcDto.File -> SwitcherVirtualFile(rpcDto)
  }
}

@Service(Service.Level.PROJECT)
private class RecentFilesCoroutineScopeProvider(val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): RecentFilesCoroutineScopeProvider {
      return project.service<RecentFilesCoroutineScopeProvider>()
    }
  }
}