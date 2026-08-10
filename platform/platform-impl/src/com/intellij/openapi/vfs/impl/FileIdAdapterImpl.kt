// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.FileIdAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.platform.fileEditor.FileEntry

internal class FileIdAdapterImpl : FileIdAdapter {

  override fun getFile(fileEntry: FileEntry, virtualFileManager: VirtualFileManager, project: Project): VirtualFile? {
    return virtualFileManager.findFileByUrl(fileEntry.url)
           ?: virtualFileManager.refreshAndFindFileByUrl(fileEntry.url)
  }

  override fun getId(file: VirtualFile): Int? = (file as? VirtualFileWithId)?.id
  override fun getManagingFsCreationTimestamp(file: VirtualFile): Long {
    return LOG.runAndLogException { ManagingFS.getInstance().creationTimestamp } ?: -1
  }
  override fun getProtocol(file: VirtualFile): String? = null
  override fun getUrl(file: VirtualFile): String = file.url

  override fun shouldSaveEditorState(file: VirtualFile): Boolean = true

  companion object {
    private val LOG = logger<FileIdAdapterImpl>()
  }
}
