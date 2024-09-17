// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VirtualFileUrlBridge(
  id: Int,
  manager: VirtualFileUrlManagerImpl,
  private val urlCanonicallyCased: Boolean = false,
) :
  VirtualFileUrlImpl(id, manager), VirtualFilePointer {
  @Volatile
  private var file: VirtualFile? = null

  @Volatile
  private var timestampOfCachedFiles = -1L

  override fun getFile() = findVirtualFile()
  override fun isValid() = findVirtualFile() != null
  override fun toString() = url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlBridge

    return id == other.id
  }

  override fun hashCode(): Int = id

  private fun findVirtualFile(): VirtualFile? {
    val fileManager = VirtualFileManager.getInstance()
    val timestamp = timestampOfCachedFiles
    val cachedResults = file
    return if (timestamp == fileManager.modificationCount) cachedResults
    else {
      file = if (urlCanonicallyCased) {
        fileManager.findFileByCanonicallyCasedUrl(url)
      } else {
        fileManager.findFileByUrl(url)
      }
      timestampOfCachedFiles = fileManager.modificationCount
      file
    }
  }
}