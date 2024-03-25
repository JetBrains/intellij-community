// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.CommonProcessors
import com.intellij.util.containers.ContainerUtil

/**
 * @param root $ROOT_CONFIG$ to watch (aka <config>, idea.config.path)
 */
internal class SharedConfigFolderVfsListener(private val root: VirtualFile) : BulkFileListener {
  fun init() {
    VfsUtilCore.processFilesRecursively(root, CommonProcessors.alwaysTrue()) // ensure everything in VFS
    VfsUtil.markDirtyAndRefresh(false, true, true, root)

    ApplicationManager.getApplication().messageBus.connect()
      .subscribe(VirtualFileManager.VFS_CHANGES, SharedConfigFolderVfsListener(root))
    LocalFileSystem.getInstance().addRootToWatch(root.path, true)
  }

  override fun after(events: MutableList<out VFileEvent>) {
    val modified = mutableSetOf<String>()
    val deleted = mutableSetOf<String>()

    for (event in events) {
      if (!event.isFromRefresh) continue
      val file = event.file ?: continue
      if (!VfsUtil.isAncestor(root, file, true)) continue
      when (event) {
        is VFileCreateEvent,
        is VFileCopyEvent,
        is VFileContentChangeEvent -> {
          ContainerUtil.addIfNotNull(modified, getSpecFor(file))
        }
        is VFileDeleteEvent -> {
          ContainerUtil.addIfNotNull(deleted, getSpecFor(file))
        }
        is VFileMoveEvent -> {
          ContainerUtil.addIfNotNull(deleted, getSpecFor(event.oldPath))
          ContainerUtil.addIfNotNull(modified, getSpecFor(file))
        }
        is VFilePropertyChangeEvent -> {
          if (event.isRename) {
            ContainerUtil.addIfNotNull(deleted, getSpecFor(event.oldPath))
            ContainerUtil.addIfNotNull(modified, getSpecFor(event.newPath))
          }
        }
      }
    }

    SharedConfigFolderUtil.reloadComponents(modified, deleted)
  }

  private fun getSpecFor(file: VirtualFile): String? {
    return VfsUtil.getRelativePath(file, root, '/')
  }

  private fun getSpecFor(file: String): String? {
    val rootPath = root.path
    if (FileUtil.isAncestor(rootPath, file, true)) {
      return FileUtil.getRelativePath(rootPath, file, '/')
    }
    return null
  }
}