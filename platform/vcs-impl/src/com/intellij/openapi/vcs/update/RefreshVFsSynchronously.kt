// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.update.UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object RefreshVFsSynchronously {
  @JvmStatic
  fun updateAllChanged(updatedFiles: UpdatedFiles) {
    val callback = FilesToRefreshCollector()
    iterateFileGroupFilesDeletedOnServerFirst(updatedFiles, callback)
    refreshDeletedOrReplaced(callback.toRefreshDeletedOrReplaced)
    refreshFiles(callback.toRefresh)
  }

  @JvmStatic
  fun refreshFiles(files: Collection<File>) {
    val filesToRefresh = mutableSetOf<VirtualFile>()
    for (file in files) {
      val vf = findFirstValidVirtualParent(file)
      if (vf != null) {
        filesToRefresh.add(vf)
      }
    }
    markDirtyAndRefresh(false, false, false, *filesToRefresh.toTypedArray())
  }

  private fun refreshDeletedOrReplaced(deletedOrReplaced: Collection<File>) {
    val filesToRefresh = mutableSetOf<VirtualFile>()
    for (file in deletedOrReplaced) {
      val parent = file.parentFile
      val vf = findFirstValidVirtualParent(parent)
      if (vf != null) {
        filesToRefresh.add(vf)
      }
    }
    markDirtyAndRefresh(false, true, false, *filesToRefresh.toTypedArray())
  }

  private fun findFirstValidVirtualParent(file: File?): VirtualFile? {
    val lfs = LocalFileSystem.getInstance()
    var vf: VirtualFile? = null
    var current = file
    while (current != null && (vf == null || !vf.isValid)) {
      vf = lfs.findFileByIoFile(current)
      current = current.parentFile
    }
    return if (vf == null || !vf.isValid) null else vf
  }

  @JvmStatic
  fun updateChangesForRollback(changes: List<Change>) = updateChangesImpl(changes, REVERSED_CHANGE_WRAPPER)

  @JvmStatic
  fun updateChanges(changes: Collection<Change>) = updateChangesImpl(changes, CHANGE_WRAPPER)

  private fun <T> updateChangesImpl(changes: Collection<T>, wrapper: Wrapper<T>) {
    val files = mutableSetOf<File>()
    val deletedFiles = mutableSetOf<File>()
    changes.forEach { change ->
      val beforePath = wrapper.getBeforePath(change)
      val afterPath = wrapper.getAfterPath(change)

      beforePath?.let {
        (if (wrapper.isBeforePathDeleted(change)) deletedFiles else files) += it.ioFile
      }
      afterPath?.let {
        if (it != beforePath) files += it.ioFile
      }
    }
    refreshFiles(files)
    refreshDeletedOrReplaced(deletedFiles)
  }
}

private val CHANGE_WRAPPER = ChangeWrapper(false)
private val REVERSED_CHANGE_WRAPPER = ChangeWrapper(true)

private class ChangeWrapper(private val isReversed: Boolean) : Wrapper<Change> {
  private fun getBeforeRevision(change: Change): ContentRevision? = change.run { if (isReversed) afterRevision else beforeRevision }
  private fun getAfterRevision(change: Change): ContentRevision? = change.run { if (isReversed) beforeRevision else afterRevision }

  override fun getBeforePath(change: Change): FilePath? = getBeforeRevision(change)?.file
  override fun getAfterPath(change: Change): FilePath? = getAfterRevision(change)?.file

  override fun isBeforePathDeleted(change: Change): Boolean =
    change.run { getAfterRevision(this) == null || isMoved || isRenamed || isIsReplaced }
}

private interface Wrapper<T> {
  fun getBeforePath(change: T): FilePath?
  fun getAfterPath(change: T): FilePath?
  fun isBeforePathDeleted(change: T): Boolean
}

private class FilesToRefreshCollector : UpdateFilesHelper.Callback {
  val toRefresh = mutableSetOf<File>()
  val toRefreshDeletedOrReplaced = mutableSetOf<File>()

  override fun onFile(filePath: String, groupId: String) {
    val file = File(filePath)
    if (FileGroup.REMOVED_FROM_REPOSITORY_ID == groupId || FileGroup.MERGED_WITH_TREE_CONFLICT.endsWith(groupId)) {
      toRefreshDeletedOrReplaced.add(file)
    }
    else {
      toRefresh.add(file)
    }
  }
}