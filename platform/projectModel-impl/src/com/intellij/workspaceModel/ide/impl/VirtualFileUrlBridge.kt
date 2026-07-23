// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.impl.VirtualFileUrlWithVirtualFile
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VirtualFileUrlBridge(id: Int, manager: VirtualFileUrlManagerImpl) :
  VirtualFileUrlImpl(id, manager), VirtualFilePointer, VirtualFileUrlWithVirtualFile {

  private val fileFinder = CachedVirtualFileFinder()

  override fun getFile(): VirtualFile? = fileFinder.findVirtualFile(url)
  override fun isValid(): Boolean = fileFinder.findVirtualFile(url) != null
  override fun toString(): String = url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlBridge

    return id == other.id
  }

  override fun hashCode(): Int = id

  override fun cacheVirtualFile(file: VirtualFile) {
    fileFinder.cacheVirtualFile(file)
  }
}
