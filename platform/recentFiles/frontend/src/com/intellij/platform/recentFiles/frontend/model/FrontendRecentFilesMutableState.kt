// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.icons.AllIcons
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.SwitcherVirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesMutableState
import com.intellij.platform.recentFiles.shared.RecentFilesState
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
import com.intellij.util.IconUtil
import kotlinx.coroutines.flow.MutableStateFlow

internal class FrontendRecentFilesMutableState(project: Project) : RecentFilesMutableState<SwitcherVirtualFile>(project) {
  override fun convertDtoToModel(rpcDto: SwitcherRpcDto): SwitcherVirtualFile {
    return when (rpcDto) {
      is SwitcherRpcDto.File -> SwitcherVirtualFile(rpcDto)
    }
  }

  override suspend fun convertVirtualFileIdToModel(virtualFileId: VirtualFileId): SwitcherVirtualFile? {
    val localFile = virtualFileId.virtualFile() ?: return null
    return convertVirtualFileToViewModel(localFile, project)
  }

  override fun convertModelToVirtualFile(model: SwitcherVirtualFile): VirtualFile? {
    return model.virtualFile
  }

  override fun checkValidity(model: SwitcherVirtualFile): Boolean {
    return model.virtualFile?.isValid != false
  }

  fun chooseStateToReadFrom(filesKind: RecentFileKind): MutableStateFlow<RecentFilesState<SwitcherVirtualFile>> {
    return when (filesKind) {
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFilesState
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFilesState
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> {
        // If there is only one opened file, users will benefit more from the entire _recently opened_ files list
        val capturedSwitcherModelState = recentlyOpenedPinnedFilesState.value.entries
        when {
          capturedSwitcherModelState.size == 1 && isSingleFileOpenedInMultipleEditors(capturedSwitcherModelState.single().virtualFile) -> recentlyOpenedPinnedFilesState
          capturedSwitcherModelState.isEmpty() || capturedSwitcherModelState.size == 1 -> recentlyOpenedFilesState
          else -> recentlyOpenedPinnedFilesState
        }
      }
    }
  }

  private fun isSingleFileOpenedInMultipleEditors(file: VirtualFile?): Boolean {
    if (file == null) return false
    return FileEditorManager.getInstance(project).getAllEditors(file).size > 1
  }
}

internal suspend fun convertVirtualFileToViewModel(virtualFile: VirtualFile, project: Project): SwitcherVirtualFile {
  val (fileName, localIcon) = readAction {
    if (!(virtualFile.isValid)) return@readAction "invalidated file" to AllIcons.Empty
    val fileName = virtualFile.name
    val icon = IconUtil.getIcon(virtualFile, 0, project)
    fileName to icon
  }
  return SwitcherVirtualFile(virtualFile, fileName, localIcon)
}