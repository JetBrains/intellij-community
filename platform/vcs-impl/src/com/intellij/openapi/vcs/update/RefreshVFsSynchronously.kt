// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vcs.changes.Change
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
  fun updateChangesForRollback(changes: List<Change>) = updateChangesImpl(changes, RollbackChangeWrapper)

  @JvmStatic
  fun updateChanges(changes: Collection<Change>) = updateChangesImpl(changes, DirectChangeWrapper)

  private fun updateChangesImpl(changes: Collection<Change>, wrapper: ChangeWrapper) {
    val deletedOrReplaced = mutableSetOf<File>()
    val toRefresh = mutableSetOf<File>()
    for (change in changes) {
      if (!wrapper.beforeNull(change) && (wrapper.movedOrRenamedOrReplaced(change) || wrapper.afterNull(change))) {
        deletedOrReplaced.add(wrapper.getBeforeFile(change)!!)
      }
      else if (!wrapper.beforeNull(change)) {
        toRefresh.add(wrapper.getBeforeFile(change)!!)
      }
      if (!wrapper.afterNull(change) &&
          (wrapper.beforeNull(change) || !Comparing.equal(change.afterRevision!!.file, change.beforeRevision!!.file))
      ) {
        toRefresh.add(wrapper.getAfterFile(change)!!)
      }
    }
    refreshFiles(toRefresh)
    refreshDeletedOrReplaced(deletedOrReplaced)
  }
}

private object RollbackChangeWrapper : ChangeWrapper {
  override fun beforeNull(change: Change): Boolean = change.afterRevision == null
  override fun afterNull(change: Change): Boolean = change.beforeRevision == null

  override fun getBeforeFile(change: Change): File? = if (beforeNull(change)) null else change.afterRevision!!.file.ioFile
  override fun getAfterFile(change: Change): File? = if (afterNull(change)) null else change.beforeRevision!!.file.ioFile

  override fun movedOrRenamedOrReplaced(change: Change): Boolean = change.isMoved || change.isRenamed || change.isIsReplaced
}

private object DirectChangeWrapper : ChangeWrapper {
  override fun beforeNull(change: Change): Boolean = change.beforeRevision == null
  override fun afterNull(change: Change): Boolean = change.afterRevision == null

  override fun getBeforeFile(change: Change): File? = if (beforeNull(change)) null else change.beforeRevision!!.file.ioFile
  override fun getAfterFile(change: Change): File? = if (afterNull(change)) null else change.afterRevision!!.file.ioFile

  override fun movedOrRenamedOrReplaced(change: Change): Boolean = change.isMoved || change.isRenamed || change.isIsReplaced
}

private interface ChangeWrapper {
  fun beforeNull(change: Change): Boolean
  fun afterNull(change: Change): Boolean
  fun getBeforeFile(change: Change): File?
  fun getAfterFile(change: Change): File?
  fun movedOrRenamedOrReplaced(change: Change): Boolean
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