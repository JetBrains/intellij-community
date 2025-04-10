// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.*
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.IconUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class FrontendRecentFilesModel(private val project: Project) {
  private val modelUpdateScope = RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.childScope("RecentFilesModel updates")

  private val recentlyOpenedFilesState = MutableStateFlow(RecentFilesState())
  private val recentlyEditedFilesState = MutableStateFlow(RecentFilesState())
  private val recentlyOpenedPinnedFilesState = MutableStateFlow(RecentFilesState())

  fun getRecentFiles(fileKind: RecentFileKind): List<SwitcherVirtualFile> {
    val capturedModelState = chooseState(fileKind).value.entries
    val filteredModel = capturedModelState.filter {  fileModel ->
      val file = fileModel.virtualFile ?: return@filter true
      val excluder = RecentFilesExcluder.EP_NAME.findFirstSafe { ext ->
        when (fileKind) {
          RecentFileKind.RECENTLY_EDITED -> ext.isExcludedFromRecentlyEdited(project, file)
          RecentFileKind.RECENTLY_OPENED, RecentFileKind.RECENTLY_OPENED_UNPINNED -> ext.isExcludedFromRecentlyOpened(project, file)
        }
      }
      excluder == null
    }
    if (LOG.isDebugEnabled) {
      LOG.debug(buildString {
        append("Return requested $fileKind list: ${capturedModelState.joinToString { it.virtualFile?.name ?: "null" }}")
        if (filteredModel.size != capturedModelState.size) {
          append("\nAfter filtering: ${filteredModel.joinToString { it.virtualFile?.name ?: "null" }}")
        }
      })
    }

    return filteredModel
  }

  fun applyFrontendChanges(filesKind: RecentFileKind, files: List<VirtualFile>, isAdded: Boolean) {
    modelUpdateScope.launch {
      val frontendStateToUpdate = chooseState(filesKind)
      val fileModels = files.map { convertVirtualFileToViewModel(it, project) }

      frontendStateToUpdate.update { oldList ->
        if (isAdded) {
          val maybeItemsWithRichMetadata = oldList.entries.associateBy { it }
          val effectiveModelsToInsert = fileModels.map { fileModel -> maybeItemsWithRichMetadata[fileModel] ?: fileModel }
          RecentFilesState(effectiveModelsToInsert + (oldList.entries - effectiveModelsToInsert.toSet()))
        }
        else {
          RecentFilesState(oldList.entries - fileModels.toSet())
        }
      }

      if (isAdded) {
        FileSwitcherApi.getInstance().updateRecentFilesBackendState(createFilesUpdateRequest(filesKind, files, project))
      }
      else {
        FileSwitcherApi.getInstance().updateRecentFilesBackendState(createHideFilesRequest(filesKind, files, project))
      }
    }
  }

  suspend fun fetchInitialData(targetFilesKind: RecentFileKind, project: Project) {
    FileSwitcherApi.getInstance().updateRecentFilesBackendState(createFilesSearchRequestRequest(recentFileKind = targetFilesKind, project = project))
  }

  suspend fun subscribeToBackendRecentFilesUpdates(targetFilesKind: RecentFileKind) {
    LOG.debug("Started collecting recent files updates for kind: $targetFilesKind")
    FileSwitcherApi.getInstance()
      .getRecentFileEvents(targetFilesKind, project.projectId())
      .collect { update -> applyChangesFromBackendToModel(update, targetFilesKind) }
  }

  private suspend fun applyChangesFromBackendToModel(change: RecentFilesEvent, targetFilesKind: RecentFileKind) {
    val targetModel = chooseState(targetFilesKind)
    when (change) {
      is RecentFilesEvent.ItemsAdded -> {
        val toAdd = change.batch.map(::convertSwitcherDtoToViewModel)
        LOG.debug("Adding ${change.batch.size} items to $targetFilesKind frontend model")
        targetModel.update { oldList ->
          RecentFilesState(toAdd + (oldList.entries - toAdd.toSet()))
        }
      }
      is RecentFilesEvent.ItemsUpdated -> {
        val itemsToMergeWithExisting = change.batch.map(::convertSwitcherDtoToViewModel).associateBy { it }
        LOG.debug("Updating ${change.batch.size} items in $targetFilesKind frontend model")
        targetModel.update { oldList ->
          if (change.putOnTop) {
            val newValuesToPutIntoFirstPosition = oldList.entries.mapNotNull { oldItem -> itemsToMergeWithExisting[oldItem] }
            val restOfExistingValues = oldList.entries - newValuesToPutIntoFirstPosition.toSet()
            RecentFilesState(newValuesToPutIntoFirstPosition + restOfExistingValues)
          }
          else {
            val effectiveModelsToInsert = oldList.entries.map { oldItem -> itemsToMergeWithExisting[oldItem] ?: oldItem }
            RecentFilesState(effectiveModelsToInsert)
          }
        }
      }
      is RecentFilesEvent.ItemsRemoved -> {
        LOG.debug("Removing ${change.batch.size} items from $targetFilesKind frontend model")
        val toRemove = change.batch.mapNotNull { virtualFileId -> convertVirtualFileIdToViewModel(virtualFileId, project) }
        targetModel.update { oldList ->
          RecentFilesState(oldList.entries - toRemove.toSet())
        }
      }
      is RecentFilesEvent.AllItemsRemoved -> {
        LOG.debug("Removing all items from $targetFilesKind frontend model")
        targetModel.update { RecentFilesState(listOf()) }
      }
      is RecentFilesEvent.UncertainChangeOccurred -> {
        LOG.debug("Updating all items in $targetFilesKind frontend model because of undetermined backend IDE state change")
        val targetState = chooseState(targetFilesKind).value.entries.mapNotNull { it.virtualFile }
        FileSwitcherApi.getInstance().updateRecentFilesBackendState(createFilesUpdateRequest(targetFilesKind, targetState, project))
      }
    }
  }

  private fun chooseState(filesKind: RecentFileKind): MutableStateFlow<RecentFilesState> {
    return when (filesKind) {
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFilesState
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFilesState
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> recentlyOpenedPinnedFilesState
    }
  }

  private fun convertSwitcherDtoToViewModel(rpcDto: SwitcherRpcDto): SwitcherVirtualFile {
    return when (rpcDto) {
      is SwitcherRpcDto.File -> SwitcherVirtualFile(rpcDto)
    }
  }

  private suspend fun convertVirtualFileToViewModel(virtualFile: VirtualFile, project: Project): SwitcherVirtualFile {
    val localIcon = readAction { IconUtil.getIcon(virtualFile, 0, project) }
    return SwitcherVirtualFile(virtualFile, localIcon, project)
  }

  private suspend fun convertVirtualFileIdToViewModel(virtualFileId: VirtualFileId, project: Project): SwitcherVirtualFile? {
    val localFile = virtualFileId.virtualFile() ?: return null
    return convertVirtualFileToViewModel(localFile, project)
  }

  companion object {
    fun getInstance(project: Project): FrontendRecentFilesModel {
      return project.service<FrontendRecentFilesModel>()
    }

    suspend fun getInstanceAsync(project: Project): FrontendRecentFilesModel {
      return project.serviceAsync<FrontendRecentFilesModel>()
    }
  }
}

private class RecentFilesState(val entries: List<SwitcherVirtualFile> = listOf())