// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

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
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import com.intellij.platform.recentFiles.shared.createFilesUpdateRequest
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class FrontendRecentFilesModel(private val project: Project) {
  private val modelUpdateScope = RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.childScope("RecentFilesModel updates")

  private val modelState = FrontendRecentFilesMutableState(project)

  fun getRecentFiles(fileKind: RecentFileKind): List<SwitcherVirtualFile> {
    val capturedModelState = modelState.chooseStateToReadFrom(fileKind).value.entries
    val filteredModel = capturedModelState.filter { fileModel ->
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
      val frontendStateToUpdate = modelState.chooseStateToWriteTo(filesKind)
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
      .collect { update -> modelState.applyChangesToModel(update, targetFilesKind) }
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