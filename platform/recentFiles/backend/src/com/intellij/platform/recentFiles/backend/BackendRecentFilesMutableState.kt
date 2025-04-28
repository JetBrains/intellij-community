// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.model.RecentFilesMutableState
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto

internal class BackendRecentFilesMutableState(project: Project) : RecentFilesMutableState<VirtualFile>(project) {
  override fun convertDtoToModel(rpcDto: SwitcherRpcDto): VirtualFile? {
    return when (rpcDto) {
      is SwitcherRpcDto.File -> rpcDto.virtualFileId.virtualFile()
    }
  }

  override suspend fun convertVirtualFileIdToModel(virtualFileId: VirtualFileId): VirtualFile? {
    return virtualFileId.virtualFile()
  }

  override fun convertModelToVirtualFile(model: VirtualFile): VirtualFile? {
    return model
  }

  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return chooseStateToWriteTo(filesKind).value.entries
  }
}