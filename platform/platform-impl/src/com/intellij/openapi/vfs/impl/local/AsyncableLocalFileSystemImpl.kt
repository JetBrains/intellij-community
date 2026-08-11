// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.impl.async.AbstractContentWriteTask
import com.intellij.openapi.vfs.impl.async.BackpressureBySizeAndTaskCount
import com.intellij.openapi.vfs.impl.async.MAX_ASYNC_UPDATES_POSTPONED
import com.intellij.openapi.vfs.impl.async.MAX_ASYNC_UPDATES_SIZE_POSTPONED
import com.intellij.openapi.vfs.newvfs.AsyncableFileSystem
import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor
import com.intellij.util.concurrency.SequentialTaskExecutor.createSequentialApplicationPoolExecutor
import com.intellij.util.io.UnsyncByteArrayInputStream
import com.intellij.util.io.UnsyncByteArrayOutputStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path


/**
 * [LocalFileSystemImpl] with support for async IO, as per [AsyncableFileSystem].
 * Specifically, it supports asynchronous file content writing.
 *
 * This class is a plain inlining of [com.intellij.openapi.vfs.impl.async.AsyncableFileSystemWrapper].
 * The reason for subclassing instead of delegation is that a lot of code use [com.intellij.openapi.vfs.LocalFileSystem]
 * and even [LocalFileSystemImpl] directly, i.e., use implementation-specific methods of the local file system.
 * Hence, we can't just hide [com.intellij.openapi.vfs.LocalFileSystem] behind [com.intellij.openapi.vfs.impl.async.AsyncableFileSystemWrapper],
 * without breaking all that code.
 */
@Internal
@Suppress("removal")
class AsyncableLocalFileSystemImpl : LocalFileSystemImpl(), AsyncableFileSystem {

  //MAYBE RC: Write more tests?

  private val ioTasksExecutor = AsyncableFileIOTaskExecutor(
    BackpressureBySizeAndTaskCount(MAX_ASYNC_UPDATES_POSTPONED, MAX_ASYNC_UPDATES_SIZE_POSTPONED)
  ) {
    createSequentialApplicationPoolExecutor("AsyncContentWriter[fs:$PROTOCOL]")
  }

  override fun dispose() {
    try {
      ioTasksExecutor.close()
    }
    catch (e: Exception) {
      LOG.warn("Failed to close async local file writer", e)
    }

    super.dispose()
  }

  // <editor-fold desc="AsyncableFileSystem methods:"> ============================================================================== //

  override fun hasUnfinishedTasks(): Boolean = ioTasksExecutor.hasUnfinishedTasks()

  override fun hasUnfinishedTasksFor(file: VirtualFile): Boolean {
    val fileId = fileIdOf(file)
    if (fileId <= 0) {
      return false
    }
    return ioTasksExecutor.hasUnfinishedTasksFor(fileId)
  }

  @Throws(IOException::class)
  override fun fsync() {
    ioTasksExecutor.flush()
  }

  @Throws(IOException::class)
  override fun fsync(file: VirtualFile) {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      ioTasksExecutor.flush(fileId)
    }
  }

  // </editor-fold> ================================================================================================================ //

  // <editor-fold desc="asynchronous FileSystem methods overrides: "> ============================================================== //

  /** true if already inside an asyncable task, false otherwise */
  private val recursionGuard = ThreadLocal.withInitial { false }

  //MAYBE RC: provide method writeContent(file..., content, contentLength), to skip array copying? -- important for large files
  override fun getOutputStream(file: VirtualFile, requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      return object : UnsyncByteArrayOutputStream() {
        private var closed = false

        @Throws(IOException::class)
        override fun close() {
          if (closed) {
            return
          }
          super.close()
          closed = true

          ioTasksExecutor.execute(object : AbstractContentWriteTask(
            fileId, file,
            requestor,
            modStamp, timeStamp, timeStampUpdateRequestedByCaller = (timeStamp > 0),
            myBuffer, myCount,
          ) {
            override fun write() {
              recursionGuard.set(true)
              try {
                super@AsyncableLocalFileSystemImpl.getOutputStream(this.file, this.requestor, this.modStamp, /* timeStamp: */ -1).use {
                  it.write(content, 0, contentLength)
                }
              }
              finally {
                recursionGuard.set(false)
              }
            }

            override fun updateLastModifiedTimeOfUnderlyingFile(lastModified: Long) {
              recursionGuard.set(true)
              try {
                super@AsyncableLocalFileSystemImpl.setTimeStamp(file, lastModified)
              }
              finally {
                recursionGuard.set(false)
              }
            }

            override fun lastModifiedTimeOfUnderlyingFile(): Long {
              //It is crucial to call methods of LocalFileSystemImpl, not the overridden methods of AsyncableLocalFileSystemImpl.
              // It is tricky to do: even if one calls a LocalFileSystemImpl method via super@, that method may call other
              // LocalFileSystemImpl method(s), and this other method could be overridden in AsyncableLocalFileSystemImpl.
              //
              // This is why we can't just call super@AsyncableLocalFileSystemImpl.getTimeStamp(file) here:
              // LocalFileSystemImpl.getTimeStamp(file) delegates to .getAttributes(file) which is overridden =>
              // AsyncableLocalFileSystemImpl.getAttribute(file) is actually called => returns this.timeStamp, instead
              // of actual FS file.lastModified.
              //
              // So, instead, we call super.getAttributes() and rely on its actual implementation to not call anything
              // overridden -- which is true for now but fragile.
              // This is why you should always use delegation instead of inheritance, if at all possible.

              val attributes = super@AsyncableLocalFileSystemImpl.getAttributes(this.file)
              return attributes?.lastModified ?: DEFAULT_TIMESTAMP
            }
          })
        }
      }
    }
    //don't know how to deal with VirtualFile without fileId -- delegate this problem to superclass:
    fsync()
    return super.getOutputStream(file, requestor, modStamp, timeStamp)
  }

  //TODO RC:  fsync(file) + op(file) is not thread-safe, because it could be a newer task for the same file is already enqueued
  //          and started execution in between fsync() and op(). Better solution is to wrap op(file) into FileIOTask, and execute
  //          the task via ioTasksExecutor -- ioTasksExecutor guarantees tasks execution don't overlap.
  //          ...this idea fails because currently the coalescing in AsyncableFileIOTaskExecutor is too primitive: ioTasksExecutor
  //          could 'overwrite' older task with newer one, if the older task is still pending -- which means that 'ContentWriteTask'
  //          could be just washed away by 'setTimestamp' task, so content writing disappears.
  //          Current ioTasksExecutor's coalescing strategy works well only for same-type tasks -- not for the heterogeneous
  //          tasks. We should either implement more advanced coalescing (likely significant redesign will be needed), or just
  //          leave current fsync(file) + op(file) approach, relying for consistency on a WriteAction being around.

  @Throws(IOException::class)
  override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
    fsync(file)
    super.setTimeStamp(file, timeStamp)
  }

  @Throws(IOException::class)
  override fun setWritable(file: VirtualFile, writableFlag: Boolean) {
    fsync(file)
    super.setWritable(file, writableFlag)
  }

  @Throws(IOException::class)
  override fun deleteFile(requestor: Any?, file: VirtualFile) {
    fsync(file)
    super.deleteFile(requestor, file)
  }

  @Throws(IOException::class)
  override fun moveFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile) {
    fsync(file)
    super.moveFile(requestor, file, newParent)
  }

  @Throws(IOException::class)
  override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
    fsync(file)
    super.renameFile(requestor, file, newName)
  }

  @Throws(IOException::class)
  override fun copyFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    fsync(file)
    return super.copyFile(requestor, file, newParent, copyName)
  }

  override fun getTimeStamp(file: VirtualFile): Long {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      val (unfinishedWrite, writeFailure) = unfinishedWriteOrError(fileId)
      if (unfinishedWrite != null) {
        return unfinishedWrite.timeStamp
      }
      else if (writeFailure != null) {
        throw writeFailure
      }
    }
    return super.getTimeStamp(file)
  }

  override fun getLength(file: VirtualFile): Long {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      val (unfinishedWrite, writeFailure) = unfinishedWriteOrError(fileId)
      if (unfinishedWrite != null) {
        return unfinishedWrite.contentLength.toLong()
      }
      else if (writeFailure != null) {
        throw writeFailure
      }
    }
    return super.getLength(file)
  }

  @Throws(IOException::class)
  override fun contentsToByteArray(file: VirtualFile): ByteArray {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      val (unfinishedWrite, writeFailure) = unfinishedWriteOrError(fileId)
      if (unfinishedWrite != null) {
        return unfinishedWrite.content.copyOf(unfinishedWrite.contentLength)
      }
      else if (writeFailure != null) {
        throw writeFailure
      }
    }
    return super.contentsToByteArray(file)
  }

  @Throws(IOException::class)
  override fun getInputStream(file: VirtualFile): InputStream {
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      val (unfinishedWrite, writeFailure) = unfinishedWriteOrError(fileId)
      if (unfinishedWrite != null) {
        //avoid copying:
        return UnsyncByteArrayInputStream(unfinishedWrite.content, 0, unfinishedWrite.contentLength)
      }
      else if (writeFailure != null) {
        throw writeFailure
      }
    }
    return super.getInputStream(file)
  }

  override fun refresh(asynchronous: Boolean) {
    fsync()
    super.refresh(asynchronous)
  }

  override fun refreshNioFilesInternal(files: Iterable<Path?>) {
    fsync()
    super.refreshNioFilesInternal(files)
  }

  @TestOnly
  override fun cleanupForNextTest() {
    super.cleanupForNextTest()
    fsync()
  }

  override fun getAttributes(file: VirtualFile): FileAttributes? {
    //It is crucial to _first_ get unfinishedWriteOrError() tuple, and only _after_ request rawIoAttributes.
    // Otherwise, it could happen that rawIoAttributes are taken _before_ an async task is executed,
    // but unfinishedWriteOrError() is called _after_ the async task has already finished (=null)
    // => in this case rawIoAttributes are returned as-is, without accounting for all the changes carried by
    // the async task that is _already finished_.
    // Such an execution scenario is inconsistent with the notion that changes are (conceptually) 'already applied'
    // at the moment the apt method (outputStream.close()) is finished, even if implementation-wise, the changes are
    // still in-flight at the method end.
    // (The same statement about ordering is true for other methods, too -- but in other methods this order 'first
    // unfinishedWriteOrError() then super.xxx()' is somewhat natural -- this method is the only one that needs
    // special attention)
    val fileId = fileIdOf(file)
    if (fileId > 0) {
      val (unfinishedWrite, writeFailure) = unfinishedWriteOrError(fileId)
      if (unfinishedWrite != null) {
        val rawIoAttributes = super.getAttributes(file)
        if (rawIoAttributes == null) {
          //This branch shouldn't happen: in VFS create-file and write-file content are separate methods,
          // => one can't write content into a not-yet-existing file, because to write to a VirtualFile one must
          // first create a VirtualFile, which also creates a 'physical' file underneath:
          LOG.warn("file[$file].attributes is null => the file is not exist? Shouldn't be possible")
          return null
        }

        //adjust attributes as-if the task was already executed:
        return rawIoAttributes
          .withLastModified(unfinishedWrite.timeStamp)
          .withLength(unfinishedWrite.contentLength.toLong())
      }
      else if (writeFailure != null) {
        throw writeFailure
      }
    }

    return super.getAttributes(file)
  }

  override fun listWithAttributes(
    dir: VirtualFile,
    childrenNames: Set<String?>?,
  ): Map<String, FileAttributes> {
    //RC: attributes should be adjusted so pending writes look like they've been already commited -- see
    //    getTimestamp/getLength/getAttributes().
    //    But adjustment is harder for listWithAttributes() because we don't know fileId(s) of children VirtualFile(s)
    //    (maybe some/all children don't even have the fileId, because they haven't been loaded into VFS yet, and the
    //    current listWithAttributes() call is a part of the loading process).
    //    So far I haven't found a better way to implement it than just flush all async changes:
    fsync()
    return super.listWithAttributes(dir, childrenNames)
  }

  override fun getNioPath(file: VirtualFile): Path? {
    //Heuristics to help transition to asyncable FS impls: getNioPath() usually called so to then pass the Path to an
    // external process, or do something with the file using Path API -- i.e., when it is probably better having
    // pending async ops, if any, flushed.
    if (!recursionGuard.get()) {//don't call fsync() if getNioPath() is called from inside async-write-task

      //fsync(file) may look like a better idea here -- to reduce the scope of flushing -- but unfortunately it doesn't
      // work so well: in many cases .getNioPath() is invoked _on a directory_, under which many files are located,
      // including those with pending async ops, and then the directory path is passed to 'diff'/'make', etc.
      fsync()
    }
    return super.getNioPath(file)
  }

  // </editor-fold>  ================================================================================================================ //

  // <editor-fold desc="async impl internals:"> ===================================================================================== //

  private fun unfinishedWriteOrError(fileId: Int): Pair<AbstractContentWriteTask?, Throwable?> =
    ioTasksExecutor.unfinishedTaskOrError(fileId)

  // </editor-fold> =============================================================================================================== //
}

/** @return fileId if the file has it, or -1 if the file has no fileId */
private fun fileIdOf(file: VirtualFile): Int = if (file is VirtualFileWithId) file.id else -1
