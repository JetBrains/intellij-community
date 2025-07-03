// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.components.service
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileIdAdapter {

  companion object {
    fun getInstance(): FileIdAdapter = application.service()
  }

  fun getFile(id: Int, fileEntry: FileEntry?): VirtualFile?

  @ApiStatus.Experimental
  fun getFileWithTimestamp(id: Int, fileEntry: FileEntry?, managingFsCreationTimestamp: Long): VirtualFile? = getFile(id, fileEntry)

  fun getId(file: VirtualFile): Int?
  fun getManagingFsCreationTimestamp(file: VirtualFile): Long

  fun getProtocol(file: VirtualFile): String?

  fun getFile(protocol: String, path: String, fileEntry: FileEntry?): VirtualFile?

  fun shouldSaveEditorState(file: VirtualFile): Boolean

}
