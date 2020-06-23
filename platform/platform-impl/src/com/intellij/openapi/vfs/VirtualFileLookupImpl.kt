// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

internal data class VirtualFileLookupImpl(
  val localFileSystem: LocalFileSystem,
  val withRefresh: Boolean = false,
  val onlyIfCached: Boolean = false
) : VirtualFileLookup {
  override fun withRefresh() = copy(withRefresh = true)
  override fun onlyIfCached() = copy(onlyIfCached = true)

  override fun fromIoFile(file: File): VirtualFile? {
    if (localFileSystem != LocalFileSystem.getInstance()) return null
    return fromPath(FileUtil.toSystemIndependentName(file.absolutePath))
  }

  override fun fromPath(path: String): VirtualFile? {
    return when {
      onlyIfCached -> VfsImplUtil.findFileByPathIfCached(localFileSystem, path)
      withRefresh -> VfsImplUtil.refreshAndFindFileByPath(localFileSystem, path)
      else -> VfsImplUtil.findFileByPath(localFileSystem, path)
    }
  }

  override fun fromNioPath(path: Path): VirtualFile? {
    if (path.fileSystem == FileSystems.getDefault()) {
      return fromIoFile(path.toFile())
    }

    if (onlyIfCached) return null

    //TODO[jo] add EP to check for custom FS implementations for non-default filesystems
    return null
  }

  override fun fromUrl(url: String): VirtualFile? {
    val protocol = VirtualFileManager.extractProtocol(url) ?: return null
    val path = VirtualFileManager.extractPath(url)
    val fs = VirtualFileManager.getInstance().getFileSystem(protocol) ?: return null
    return when {
      fs is NewVirtualFileSystem && onlyIfCached -> fs.findFileByPathIfCached(path)
      onlyIfCached -> null
      withRefresh -> fs.refreshAndFindFileByPath(path)
      else -> fs.findFileByPath(path)
    }
  }
}

internal class VirtualFileLookupServiceImpl : VirtualFileLookupService {
  override fun newLookup(): VirtualFileLookupImpl {
    return VirtualFileLookupImpl(LocalFileSystem.getInstance())
  }

  fun newLookup(fileSystem: LocalFileSystem): VirtualFileLookupImpl {
    return VirtualFileLookupImpl(fileSystem)
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<VirtualFileLookupService>() as VirtualFileLookupServiceImpl
  }
}
