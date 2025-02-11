// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.Switcher.SwitcherPanel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModel
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModelUpdate
import com.intellij.platform.recentFiles.shared.*
import com.intellij.platform.recentFiles.shared.RecentFilesEvent.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

internal fun createAndShowNewSwitcher(onlyEditedFiles: Boolean?, event: AnActionEvent?, @Nls title: String, project: Project) {
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewSwitcherSuspend(onlyEditedFiles, event, title, project)
  }
}

@ApiStatus.Internal
suspend fun createAndShowNewSwitcherSuspend(onlyEditedFiles: Boolean?, event: AnActionEvent?, @Nls title: String, project: Project): SwitcherPanel {
  return withContext(Dispatchers.EDT) {
    SwitcherPanel(project = project,
                  title = title,
                  event = event?.inputEvent,
                  onlyEditedFiles = onlyEditedFiles,
                  givenFilesModel = createReactiveDataModel(this, project),
                  backendRequestsScope = this,
                  remoteApi = FileSwitcherApi.getInstance())
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
  return when (rpcEvent) {
    is ItemAdded -> FlowBackedListModelUpdate.ItemAdded(convertSwitcherDtoToViewModel(rpcEvent.entry))
    is ItemRemoved -> FlowBackedListModelUpdate.ItemRemoved(convertSwitcherDtoToViewModel(rpcEvent.entry))
    is AllItemsRemoved -> FlowBackedListModelUpdate.AllItemsRemoved()
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