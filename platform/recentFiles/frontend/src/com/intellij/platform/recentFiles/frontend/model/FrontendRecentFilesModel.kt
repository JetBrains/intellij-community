// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.*
import com.intellij.platform.recentFiles.shared.*
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
    val filteredModel = filterOutExcludedFiles(capturedModelState, fileKind)

    return when (fileKind) {
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> considerOpenedEditorWindowsForFiles(filteredModel)
      else -> filteredModel
    }
  }

  private fun filterOutExcludedFiles(
    capturedModelState: List<SwitcherVirtualFile>,
    fileKind: RecentFileKind,
  ): List<SwitcherVirtualFile> {
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
    LOG.trace {
      val modelData = if (filteredModel.size != capturedModelState.size)
        "After filtering: ${filteredModel.joinToString { it.virtualFile?.name ?: "null" }}"
      else
        ""
      "Return requested $fileKind list: ${capturedModelState.joinToString { it.virtualFile?.name ?: "null" }} $modelData"
    }
    return filteredModel
  }

  private fun considerOpenedEditorWindowsForFiles(filteredModel: List<SwitcherVirtualFile>): List<SwitcherVirtualFile> {
    val editorsByFile = (FileEditorManager.getInstance(project) as? FileEditorManagerImpl)
      ?.getSelectionHistoryList().orEmpty()
      .groupBy(
        keySelector = { (file, _) -> file },
        valueTransform = { (_, editor) -> editor }
      )

    return filteredModel.asSequence()
      .distinct()
      .map { fileModel ->
        val editorsForSpecificFile = editorsByFile[fileModel.virtualFile]
        if (editorsForSpecificFile.isNullOrEmpty()) {
          return@map sequenceOf(fileModel)
        }
        return@map editorsForSpecificFile.asSequence().map { editor -> fileModel.withAssociatedEditorWindow(editor) }
      }.flatten()
      .toList()
  }

  fun applyFrontendChanges(filesKind: RecentFileKind, files: List<VirtualFile>, changeKind: FileChangeKind) {
    if (files.isEmpty()) return
    LOG.trace { "Applying frontend changes for kind: $filesKind, changeKind: $changeKind, files: ${files.joinToString { it.name }}" }
    modelUpdateScope.launch {
      val frontendStateToUpdate = modelState.chooseStateToWriteTo(filesKind)
      val fileModels = files.map { convertVirtualFileToViewModel(it, project) }

      frontendStateToUpdate.update { oldList ->
        when (changeKind) {
          FileChangeKind.ADDED -> {
            val maybeItemsWithRichMetadata = oldList.entries.associateBy { it }
            val effectiveModelsToInsert = fileModels.map { fileModel -> maybeItemsWithRichMetadata[fileModel] ?: fileModel }
            RecentFilesState(effectiveModelsToInsert + (oldList.entries - effectiveModelsToInsert.toSet()))
          }
          FileChangeKind.REMOVED -> {
            RecentFilesState(oldList.entries - fileModels.toSet())
          }
          FileChangeKind.UPDATED_AND_PUT_ON_TOP -> {
            RecentFilesState(fileModels + oldList.entries - fileModels.toSet())
          }
          else -> {
            oldList
          }
        }
      }

      when (changeKind) {
        FileChangeKind.REMOVED -> {
          FileSwitcherApi.getInstance().updateRecentFilesBackendState(createHideFilesRequest(filesKind, files, project))
        }
        else -> {
          FileSwitcherApi.getInstance().updateRecentFilesBackendState(createFilesUpdateRequest(filesKind, files, true, project))
        }
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