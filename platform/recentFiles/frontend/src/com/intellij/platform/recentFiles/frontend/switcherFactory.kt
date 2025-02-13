// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.codeInsight.navigation.LOG
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.Switcher.SwitcherPanel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModelUpdate
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import com.intellij.platform.recentFiles.shared.RecentFilesEvent.*
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
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
  return withContext(Dispatchers.EDT) {
    val dataModel = createReactiveDataModel(this, project)
    val remoteApi = FileSwitcherApi.getInstance()
    val parameters = SwitcherLaunchEventParameters(event?.inputEvent)
    launch(start = CoroutineStart.UNDISPATCHED) {
      withBackgroundProgress(project, IdeBundle.message("recent.files.fetching.progress.title")) {
        remoteApi.updateRecentFilesBackendState(RecentFilesBackendRequest.NewSearchWithParameters(true == onlyEditedFiles, !parameters.isEnabled, project.projectId()))
      }
    }
    // try waiting for the initial bunch of files to load before displaying the UI
    dataModel.awaitModelPopulation(durationMillis = Registry.intValue("switcher.preload.timeout.ms", 300).toLong())
    SwitcherPanel(project = project,
                  title = title,
                  launchParameters = parameters,
                  onlyEditedFiles = onlyEditedFiles,
                  givenFilesModel = dataModel,
                  parentScope = this,
                  remoteApi = remoteApi)
  }
}

private suspend fun createReactiveDataModel(parentScope: CoroutineScope, project: Project): FlowBackedListModel<SwitcherVirtualFile> {
  val mappedEvents = FileSwitcherApi.getInstance()
    .getRecentFileEvents(project.projectId())
    .map { recentFilesUpdate -> convertRpcEventToFlowModelEvent(recentFilesUpdate) }
  return FlowBackedListModel(parentScope, mappedEvents)
}

// FIXME: Unnecessary conversion, consider setting up KX serialisation so that generic type parameter's serializer is recognised
private fun convertRpcEventToFlowModelEvent(rpcEvent: RecentFilesEvent): FlowBackedListModelUpdate<SwitcherVirtualFile> {
  LOG.debug("Switcher convert rpc to model event: $rpcEvent")
  return when (rpcEvent) {
    is ItemAdded -> FlowBackedListModelUpdate.ItemAdded(convertSwitcherDtoToViewModel(rpcEvent.entry))
    is ItemRemoved -> FlowBackedListModelUpdate.ItemRemoved(convertSwitcherDtoToViewModel(rpcEvent.entry))
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