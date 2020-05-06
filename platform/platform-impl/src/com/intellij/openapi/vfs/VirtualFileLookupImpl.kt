// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

internal data class VirtualFileLookupImpl(
  val withRefresh: Boolean = false,
  val onlyIfCached: Boolean = false
): VirtualFileLookup {
  override fun withRefresh() = copy(withRefresh = true)
  override fun onlyIfCached() = copy(onlyIfCached = true)

  override fun fromIoFile(file: File): VirtualFile? {
    val fs = realLocalFileSystem()
    return when {
      onlyIfCached -> null
      withRefresh -> fs.refreshAndFindFileByIoFile(file)
      else -> fs.findFileByIoFile(file)
    }
  }

  override fun fromPath(path: String): VirtualFile? {
    return findWithFilesSystem(realLocalFileSystem(), FileUtil.toSystemDependentName(path))
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
    val protocol = VirtualFileManager.extractProtocol(url) ?: return null
    val path = VirtualFileManager.extractPath(url)
    val fs = VirtualFileManager.getInstance().getFileSystem(protocol) ?: return null
    return findWithFilesSystem(fs, path)
  }

  private fun findWithFilesSystem(fs: VirtualFileSystem, path: String): VirtualFile? {
    return when {
      fs is NewVirtualFileSystem && onlyIfCached -> fs.findFileByPathIfCached(path)
      onlyIfCached -> null
      withRefresh -> fs.refreshAndFindFileByPath(path)
      else -> fs.findFileByPath(path)
    }
  }

  private fun realLocalFileSystem() = ApplicationManager
    .getApplication()
    .getService(LocalFileSystemImpl::class.java)
}

internal class VirtualFileLookupServiceImpl: VirtualFileLookupService {
  override fun newLookup() = VirtualFileLookupImpl()
}
