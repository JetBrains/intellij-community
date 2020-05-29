// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.util.io.URLUtil
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

internal data class VirtualFileLookupImpl(
  private val lazyActualLocalFileSystem: Lazy<LocalFileSystem>,
  private val lazyTheLocalFileSystemImpl: Lazy<LocalFileSystem>,
  private val lazyVirtualFileManager: Lazy<VirtualFileManager>,
  private val withRefresh: Boolean = false,
  private val onlyIfCached: Boolean = false
) : VirtualFileLookup {
  override fun withRefresh() = copy(withRefresh = true)
  override fun onlyIfCached() = copy(onlyIfCached = true)

  override fun fromIoFile(file: File): VirtualFile? {
    if (lazyActualLocalFileSystem.value != lazyTheLocalFileSystemImpl.value) return null
    return fromPath(FileUtil.toSystemIndependentName(file.absolutePath))
  }

  override fun fromPath(path: String): VirtualFile? {
    val localFileSystem = lazyActualLocalFileSystem.value
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
    if (onlyIfCached) return null

    val index = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    if (index < 0) return null

    val protocol = url.substring(0, index)
    val fs = lazyVirtualFileManager.value.getFileSystem(protocol) ?: return null
    val path = url.substring(index + URLUtil.SCHEME_SEPARATOR.length)

    return when {
      fs is NewVirtualFileSystem && onlyIfCached -> fs.findFileByPathIfCached(path)
      withRefresh -> fs.refreshAndFindFileByPath(path)
      else -> fs.findFileByPath(path)
    }
  }
}

internal class VirtualFileLookupServiceImpl : VirtualFileLookupService {
  private val lazyLocalFileSystem = lazy(LazyThreadSafetyMode.NONE) { LocalFileSystem.getInstance() }
  private val lazyVirtualFileManager = lazy(LazyThreadSafetyMode.NONE) { VirtualFileManager.getInstance() }

  override fun newLookup(): VirtualFileLookupImpl {
    return VirtualFileLookupImpl(lazyLocalFileSystem, lazyLocalFileSystem, lazyVirtualFileManager)
  }

  fun newLookup(fileSystem: LocalFileSystem): VirtualFileLookupImpl {
    return VirtualFileLookupImpl(lazyOf(fileSystem), lazyLocalFileSystem, lazyVirtualFileManager)
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<VirtualFileLookupService>() as VirtualFileLookupServiceImpl
  }
}
