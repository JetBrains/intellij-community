// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.impl.VirtualFileUrlWithVirtualFile
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

internal class VirtualFileUrlBridge(
  name: String,
  managerImpl: VirtualFileUrlManagerImpl,
  parent: VirtualFileUrl?,
) : VirtualFileUrlImpl(name, managerImpl, parent as VirtualFileUrlImpl?), VirtualFilePointer, VirtualFileUrlWithVirtualFile {

  private val fileFinder = CachedVirtualFileFinder()

  override fun getFile() = fileFinder.findVirtualFile(url)
  override fun isValid() = fileFinder.findVirtualFile(url) != null
  override fun toString() = url

  override fun cacheVirtualFile(file: VirtualFile) {
    fileFinder.cacheVirtualFile(file)
  }
}
