// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

internal data class VirtualFileLookupImpl(
  val withRefresh: Boolean = false
): VirtualFileLookup {
  override fun withRefresh() = copy(withRefresh = true)

  override fun fromIoFile(file: File): VirtualFile? {
    val fs = LocalFileSystem.getInstance()
    return when {
      withRefresh -> fs.refreshAndFindFileByIoFile(file)
      else -> fs.findFileByIoFile(file)
    }
  }

  override fun fromPath(path: String): VirtualFile? {
    return findWithFilesSystem(LocalFileSystem.getInstance(), FileUtil.toSystemDependentName(path))
  }

  override fun fromNioPath(path: Path): VirtualFile? {
    if (path.fileSystem == FileSystems.getDefault()) {
      return fromIoFile(path.toFile())
    }

    //TODO[jo] add EP to check for custom FS implementations for non-default filesystems
    return null
  }

  override fun fromUrl(url: String): VirtualFile? {
    val protocol = VirtualFileManager.extractProtocol(url) ?: return null
    val path = VirtualFileManager.extractPath(url)
    val fs = VirtualFileManager.getInstance().getFileSystem(protocol) ?: return null
    return findWithFilesSystem(fs, path)
  }

  private fun findWithFilesSystem(fs: VirtualFileSystem, path: String): VirtualFile? {
    return when {
      withRefresh -> fs.refreshAndFindFileByPath(path)
      else -> fs.findFileByPath(path)
    }
  }
}

internal class VirtualFileLookupServiceImpl: VirtualFileLookupService {
  override fun newLookup() = VirtualFileLookupImpl()
}
