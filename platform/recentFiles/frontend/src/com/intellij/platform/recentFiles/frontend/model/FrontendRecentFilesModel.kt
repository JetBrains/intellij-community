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
import com.intellij.platform.recentFiles.frontend.SwitcherVirtualFile
import com.intellij.platform.recentFiles.frontend.createFilesSearchRequestRequest
import com.intellij.platform.recentFiles.frontend.createFilesUpdateRequest
import com.intellij.platform.recentFiles.frontend.createHideFilesRequest
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

  private val recentlyOpenedFilesState = MutableStateFlow(listOf<SwitcherVirtualFile>())
  private val recentlyEditedFilesState = MutableStateFlow(listOf<SwitcherVirtualFile>())
  private val recentlyOpenedPinnedFilesState = MutableStateFlow(listOf<SwitcherVirtualFile>())

  fun getRecentFiles(fileKind: RecentFileKind): List<SwitcherVirtualFile> {
    return chooseState(fileKind).value
  }

  fun applyFrontendChanges(filesKind: RecentFileKind, files: List<VirtualFile>, isAdded: Boolean) {
    modelUpdateScope.launch {
      val frontendStateToUpdate = chooseState(filesKind)
      val fileModels = files.map { convertVirtualFileToViewModel(it, project) }

      frontendStateToUpdate.update { oldList ->
        if (isAdded) {
          val maybeItemsWithRichMetadata = oldList.associateBy { it }
          val effectiveModelsToInsert = fileModels.map { fileModel -> maybeItemsWithRichMetadata[fileModel] ?: fileModel }

          effectiveModelsToInsert + (oldList - effectiveModelsToInsert)
        }
        else {
          oldList - fileModels
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
    val targetModel = chooseState(targetFilesKind)
    FileSwitcherApi.getInstance()
      .getRecentFileEvents(targetFilesKind, project.projectId())
      .collect { update -> applyChangesFromBackendToModel(update, targetModel) }
  }

  private suspend fun applyChangesFromBackendToModel(change: RecentFilesEvent, targetModel: MutableStateFlow<List<SwitcherVirtualFile>>) {
    when (change) {
      is RecentFilesEvent.ItemsAdded -> {
        val toAdd = change.batch.map(::convertSwitcherDtoToViewModel)
        targetModel.update { oldList ->
          LOG.debug("Adding items ${change.batch} to frontend model")
          toAdd + (oldList - toAdd)
        }
      }
      is RecentFilesEvent.ItemsUpdated -> {
        val itemsToMergeWithExisting = change.batch.map(::convertSwitcherDtoToViewModel).associateBy { it }
        targetModel.update { oldList ->
          LOG.debug("Updating items ${change.batch} in frontend model")
          oldList.map { oldItem -> itemsToMergeWithExisting[oldItem] ?: oldItem }
        }
      }
      is RecentFilesEvent.ItemsRemoved -> {
        LOG.debug("Removing items ${change.batch} from frontend model")
        val toRemove = change.batch.mapNotNull { virtualFileId -> convertVirtualFileIdToViewModel(virtualFileId, project) }
        targetModel.update { oldList ->
          oldList - toRemove
        }
      }
      is RecentFilesEvent.AllItemsRemoved -> {
        LOG.debug("Removing all items from frontend model")
        targetModel.update { listOf() }
      }
    }
  }

  private fun chooseState(filesKind: RecentFileKind): MutableStateFlow<List<SwitcherVirtualFile>> {
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