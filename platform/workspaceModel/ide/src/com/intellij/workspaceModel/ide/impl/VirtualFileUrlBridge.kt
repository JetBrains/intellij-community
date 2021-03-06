// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl

class VirtualFileUrlBridge(id: Int, manager: VirtualFileUrlManagerImpl, initializeVirtualFileLazily: Boolean) :
  VirtualFileUrlImpl(id, manager), VirtualFilePointer {
  @Volatile
  private var file: VirtualFile? = null
  @Volatile
  private var timestampOfCachedFiles = -1L

  init {
    if (!initializeVirtualFileLazily) findVirtualFile()
  }

  override fun getFile() = findVirtualFile()
  override fun isValid() = findVirtualFile() != null
  override fun toString() = url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlBridge

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id

  private fun findVirtualFile(): VirtualFile? {
    val fileManager = VirtualFileManager.getInstance()
    val timestamp = timestampOfCachedFiles
    val cachedResults = file
    return if (timestamp == fileManager.modificationCount) cachedResults else {
      file = fileManager.findFileByUrl(url)
      timestampOfCachedFiles = fileManager.modificationCount
      file
    }
  }
}