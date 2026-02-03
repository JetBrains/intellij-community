// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil.equalsCaseSensitive
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.Path
import kotlin.system.measureTimeMillis

interface FilePathChange {
  val beforePath: FilePath?
  val afterPath: FilePath?

  class Simple(override val beforePath: FilePath?, override val afterPath: FilePath?) : FilePathChange
}

@ApiStatus.Internal
object RefreshVFsSynchronously {
  private val TRACE_LOG = Logger.getInstance("#trace.RefreshVFsSynchronously")
  private val TIME_LOG = Logger.getInstance("#time.RefreshVFsSynchronously")

  @JvmStatic
  fun trace(message: @NonNls String) {
    TRACE_LOG.debug { "RefreshVFsSynchronously: $message" }
  }

  fun updateAllChanged(updatedFiles: UpdatedFiles) {
    val collector = FilesCollector()
    UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles, collector)
    refreshDeletedFiles(collector.deletedFiles)
    refreshFiles(collector.files)
  }

  fun findVirtualFilesWithRefresh(files: List<File>): List<VirtualFile> {
    refreshFiles(files.map { it.toPath() })
    return files.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
  }

  fun refreshFiles(files: Collection<Path>) {
    if (files.isEmpty()) {
      return
    }

    if (TRACE_LOG.isDebugEnabled) {
      TRACE_LOG.debug("RefreshVFsSynchronously#refreshFiles: $files", Throwable())
    }
    val toRefresh = files.mapNotNullTo(mutableSetOf()) { findValidParent(it) }
    markDirtyAndRefresh(isRecursive = false, toRefresh)
  }

  @JvmStatic
  fun refreshVirtualFiles(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return
    if (TRACE_LOG.isDebugEnabled) {
      TRACE_LOG.debug("RefreshVFsSynchronously#refreshVirtualFiles: $files", Throwable())
    }
    markDirtyAndRefresh(isRecursive = false, files)
  }

  @JvmStatic
  fun refreshVirtualFilesRecursive(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return
    if (TRACE_LOG.isDebugEnabled) {
      TRACE_LOG.debug("RefreshVFsSynchronously#refreshVirtualFilesRecursive: $files", Throwable())
    }
    markDirtyAndRefresh(isRecursive = true, files)
  }

  private fun refreshDeletedFiles(files: Collection<Path>) {
    if (files.isEmpty()) return
    if (TRACE_LOG.isDebugEnabled) {
      TRACE_LOG.debug("RefreshVFsSynchronously#refreshDeletedFiles: $files", Throwable())
    }
    val toRefresh = files.mapNotNullTo(LinkedHashSet()) { findValidParent(it.parent) }
    markDirtyAndRefresh(isRecursive = true, toRefresh)
  }

  private fun markDirtyAndRefresh(isRecursive: Boolean, files: Collection<VirtualFile>) {
    val time = measureTimeMillis {
      runWithProgressText {
        VfsUtil.markDirtyAndRefresh(false, isRecursive, false, *files.toTypedArray())
      }
    }
    TIME_LOG.debug { "VFS refresh took ${time}ms, ${files.size} files, isRecursive=$isRecursive" }
  }

  private fun findValidParent(file: Path?): VirtualFile? {
    return generateSequence(file) { it.parent }
      .mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
      .firstOrNull { it.isValid }
  }

  @JvmStatic
  fun updateChangesForRollback(changes: List<Change>) {
    refresh(changes, REVERSED_CHANGE_WRAPPER)
  }

  @JvmStatic
  fun updateChanges(changes: Collection<Change>) {
    refresh(changes, CHANGE_WRAPPER)
  }

  fun refresh(changes: Collection<FilePathChange>, isRollback: Boolean = false) {
    refresh(changes, if (isRollback) REVERSED_FILE_PATH_CHANGE_WRAPPER else FILE_PATH_CHANGE_WRAPPER)
  }

  private fun <T> refresh(changes: Collection<T>, wrapper: Wrapper<T>) {
    val files = LinkedHashSet<Path>()
    val deletedFiles = LinkedHashSet<Path>()
    changes.forEach { change ->
      val beforePath = wrapper.getBeforePath(change)
      val afterPath = wrapper.getAfterPath(change)

      beforePath?.let {
        (if (wrapper.isBeforePathDeleted(change)) deletedFiles else files).add(it.ioFile.toPath())
      }
      afterPath?.let {
        if (it != beforePath) {
          files.add(it.ioFile.toPath())
        }
      }
    }
    refreshFiles(files)
    refreshDeletedFiles(deletedFiles)
  }
}

private fun runWithProgressText(task: () -> Unit) {
  val indicator = ProgressManager.getInstance().progressIndicator
  if (indicator == null) {
    task()
    return
  }

  val oldText = indicator.text
  if (oldText.isNullOrEmpty()) {
    indicator.text = IdeCoreBundle.message("file.synchronize.progress")
    task()
    indicator.text = oldText
  }
  else {
    val oldText2 = indicator.text2
    indicator.text2 = IdeCoreBundle.message("file.synchronize.progress")
    task()
    indicator.text2 = oldText2
  }
}

private val CHANGE_WRAPPER = ChangeWrapper(false)
private val REVERSED_CHANGE_WRAPPER = ChangeWrapper(true)
private val FILE_PATH_CHANGE_WRAPPER = FilePathChangeWrapper(false)
private val REVERSED_FILE_PATH_CHANGE_WRAPPER = FilePathChangeWrapper(true)

private class ChangeWrapper(private val isReversed: Boolean) : Wrapper<Change> {
  private fun getBeforeRevision(change: Change): ContentRevision? = change.run { if (isReversed) afterRevision else beforeRevision }
  private fun getAfterRevision(change: Change): ContentRevision? = change.run { if (isReversed) beforeRevision else afterRevision }

  override fun getBeforePath(change: Change): FilePath? = getBeforeRevision(change)?.file
  override fun getAfterPath(change: Change): FilePath? = getAfterRevision(change)?.file

  override fun isBeforePathDeleted(change: Change): Boolean =
    change.run { getAfterRevision(this) == null || isMoved || isRenamed || isIsReplaced }
}

private class FilePathChangeWrapper(private val isReversed: Boolean) : Wrapper<FilePathChange> {
  override fun getBeforePath(change: FilePathChange): FilePath? = change.run { if (isReversed) afterPath else beforePath }
  override fun getAfterPath(change: FilePathChange): FilePath? = change.run { if (isReversed) beforePath else afterPath }

  override fun isBeforePathDeleted(change: FilePathChange): Boolean =
    change.let { getAfterPath(it) == null || !equalsCaseSensitive(getBeforePath(it), getAfterPath(it)) }
}

private interface Wrapper<T> {
  fun getBeforePath(change: T): FilePath?
  fun getAfterPath(change: T): FilePath?
  fun isBeforePathDeleted(change: T): Boolean
}

private class FilesCollector : UpdateFilesHelper.Callback {
  @JvmField val files = LinkedHashSet<Path>()
  @JvmField val deletedFiles = LinkedHashSet<Path>()

  override fun onFile(filePath: String, groupId: String) {
    val file = Path.of(filePath)
    if (FileGroup.REMOVED_FROM_REPOSITORY_ID == groupId || FileGroup.MERGED_WITH_TREE_CONFLICT.endsWith(groupId)) {
      deletedFiles.add(file)
    }
    else {
      files.add(file)
    }
  }
}