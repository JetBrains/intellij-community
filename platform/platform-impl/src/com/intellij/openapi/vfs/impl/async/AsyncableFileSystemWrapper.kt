// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.async

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.AsyncableFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncFileContentWriteRequestor
import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor
import com.intellij.openapi.vfs.newvfs.persistent.executor.FileIOTaskExecutor
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.SystemProperties.getIntProperty
import com.intellij.util.SystemProperties.getLongProperty
import com.intellij.util.concurrency.SequentialTaskExecutor.createSequentialApplicationPoolExecutor
import com.intellij.util.io.IOUtil.MiB
import com.intellij.util.io.UnsyncByteArrayInputStream
import com.intellij.util.io.UnsyncByteArrayOutputStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Internal
val ASYNC_CONTENT_WRITE_ENABLED: Boolean = getBooleanProperty("vfs.async-content-write.enabled", true)

/** If more than this number of tasks are postponed -- flush all the postponed tasks synchronously */
@Internal
val MAX_ASYNC_UPDATES_POSTPONED: Int = getIntProperty("vfs.async-content-write.max-tasks-postponed", 16)

/** If more than this number of bytes are postponed -- flush all the postponed tasks synchronously */
@Internal
val MAX_ASYNC_UPDATES_SIZE_POSTPONED: Long = getLongProperty("vfs.async-content-write.max-bytes-postponed", 10 * MiB.toLong())

private val LOG = Logger.getInstance(AsyncableFileSystemWrapper::class.java)

/**
 * Wraps a [NewVirtualFileSystem], and makes some IO methods asynchronous -- i.e. doesn't execute the IO ops
 * immediately, but instead postpone it to be executed by background thread later.
 *
 * This implementation is more an example of how to implement an asynchronous file system -- it is unlikely
 * it could be used as-is, to wrap an already existing file system, because current file systems impls
 * have a number of peculiarities making delegation unfeasible.
 * E.g. many filesystems do something like `if(file.getFileSystem() != this) return null` to ensure the
 * [VirtualFile] passed in is 'their' file -- but this logic breaks if filesystem is wrapped as delegate
 * into something like [AsyncableFileSystemWrapper].
 *
 * @see com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor
 */
@Internal
class AsyncableFileSystemWrapper(
  private val delegateFileSystem: NewVirtualFileSystem,
) : NewVirtualFileSystem(), AsyncableFileSystem, AutoCloseable {


  private val ioTasksExecutor = AsyncableFileIOTaskExecutor(
    BackpressureBySizeAndTaskCount(MAX_ASYNC_UPDATES_POSTPONED, MAX_ASYNC_UPDATES_SIZE_POSTPONED)
  ) {
    createSequentialApplicationPoolExecutor("AsyncContentWriter[fs:${delegateFileSystem.protocol}]")
  }

  @VisibleForTesting
  fun delegate(): NewVirtualFileSystem = delegateFileSystem

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

  // <editor-fold desc="asynchronous FileSystem methods: "> ========================================================================== //

  //TODO RC:  fsync(file) + op(file) is not thread-safe, because it could be a newer task for the same file is already enqueued
  //          and started execution in between fsync() and op(). Better solution is to wrap op(file) into FileIOTask, and execute
  //          the task via ioTasksExecutor -- ioTasksExecutor guarantees tasks execution don't overlap.
  //          ...this idea fails because of coalescing: ioTasksExecutor could 'overwrite' older task with newer one, if the
  //          older task is still pending -- which means that 'ContentWriteTask' could be just washed away by 'setTimestamp'
  //          task, so content writing disappears in the air.
  //          Current ioTasksExecutor's coalescing strategy works well only for same-type tasks -- not for the heterogeneous
  //          tasks. We should either implement more advanced coalescing (likely significant redesign will be needed), or just
  //          leave current fsync(file) + op(file) approach, relying for consistency on a WriteAction being around (which it
  //          should be).
  @Throws(IOException::class)
  override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
    fsync(file)
    delegateFileSystem.setTimeStamp(file, timeStamp)
  }

  @Throws(IOException::class)
  override fun setWritable(file: VirtualFile, writableFlag: Boolean) {
    fsync(file)
    delegateFileSystem.setWritable(file, writableFlag)
  }

  @Throws(IOException::class)
  override fun deleteFile(requestor: Any?, file: VirtualFile) {
    fsync(file)
    delegateFileSystem.deleteFile(requestor, file)
  }

  @Throws(IOException::class)
  override fun moveFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile) {
    fsync(file)
    delegateFileSystem.moveFile(requestor, file, newParent)
  }

  @Throws(IOException::class)
  override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
    fsync(file)
    delegateFileSystem.renameFile(requestor, file, newName)
  }

  @Throws(IOException::class)
  override fun copyFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    fsync(file)
    return delegateFileSystem.copyFile(requestor, file, newParent, copyName)
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
    return delegateFileSystem.getTimeStamp(file)
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
    return delegateFileSystem.getLength(file)
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
    return delegateFileSystem.contentsToByteArray(file)
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
    return delegateFileSystem.getInputStream(file)
  }

  //MAYBE RC: provide method writeContent(file..., content, contentLength), to skip array copying -- important for large files
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
            myBuffer, myCount
          ) {
            override fun write() =
              delegateFileSystem.getOutputStream(this.file, this.requestor, this.modStamp, /* timeStamp: */ -1).use { outputStream ->
                outputStream.write(content, 0, contentLength)
              }

            override fun updateLastModifiedTimeOfUnderlyingFile(lastModified: Long) =
              delegateFileSystem.setTimeStamp(this.file, lastModified)

            override fun lastModifiedTimeOfUnderlyingFile(): Long = delegateFileSystem.getTimeStamp(this.file)
          })
        }
      }
    }
    return delegateFileSystem.getOutputStream(file, requestor, modStamp, timeStamp)
  }

  @Throws(Exception::class)
  override fun close() {
    ioTasksExecutor.close()
  }

  override fun refresh(asynchronous: Boolean) {
    fsync()
    delegateFileSystem.refresh(asynchronous)
  }

  override fun getAttributes(file: VirtualFile): FileAttributes? {
    //It is crucial to _first_ get unfinishedWriteOrError() tuple, and only _after_ request delegateFileSystem.getAttributes().
    // Otherwise, it could happen that delegateFileSystem.getAttributes() are taken _before_ an async task is executed,
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
        val rawIoAttributes = delegateFileSystem.getAttributes(file)
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

    return delegateFileSystem.getAttributes(file)
  }

  // </editor-fold>  ================================================================================================================ //

  // <editor-fold desc="async impl internals:"> ===================================================================================== //

  private fun unfinishedWriteOrError(fileId: Int): Pair<AbstractContentWriteTask?, Throwable?> =
    ioTasksExecutor.unfinishedTaskOrError(fileId)

  // </editor-fold> =============================================================================================================== //

  // <editor-fold desc="trivial delegation:"> ===================================================================================== //
  override fun refreshAndFindFileByPath(path: String): VirtualFile? = delegateFileSystem.refreshAndFindFileByPath(path)

  override fun extractRootPath(normalizedPath: String): String {
    //method is needed only for full-fledged NewFS implementation, while here we have only a delegate
    throw NotImplementedError("Method shouldn't be called")
  }

  override fun findFileByPath(path: @NonNls String): VirtualFile? = delegateFileSystem.findFileByPath(path)

  override fun findFileByPathIfCached(path: String): VirtualFile? = delegateFileSystem.findFileByPathIfCached(path)

  override fun getProtocol(): @NonNls String = delegateFileSystem.protocol

  override fun exists(file: VirtualFile): Boolean = delegateFileSystem.exists(file)

  override fun list(file: VirtualFile): Array<String> = delegateFileSystem.list(file)

  override fun isDirectory(file: VirtualFile): Boolean = delegateFileSystem.isDirectory(file)

  override fun isWritable(file: VirtualFile): Boolean = delegateFileSystem.isWritable(file)

  override fun isSymLink(file: VirtualFile): Boolean = delegateFileSystem.isSymLink(file)

  override fun resolveSymLink(file: VirtualFile): String? = delegateFileSystem.resolveSymLink(file)

  override fun findCachedFilesForPath(path: String): Iterable<VirtualFile> = delegateFileSystem.findCachedFilesForPath(path)

  override fun findFileByPathWithoutCaching(path: String): VirtualFile? = delegateFileSystem.findFileByPathWithoutCaching(path)

  @Throws(IOException::class)
  override fun createChildDirectory(requestor: Any?, parent: VirtualFile, dir: String): VirtualFile {
    return delegateFileSystem.createChildDirectory(requestor, parent, dir)
  }

  @Throws(IOException::class)
  override fun createChildFile(requestor: Any?, parent: VirtualFile, file: String): VirtualFile {
    return delegateFileSystem.createChildFile(requestor, parent, file)
  }

  override fun toString(): String = "AsyncableWrapper[$delegateFileSystem]"

  // </editor-fold> ================================================================================================================= //

}

private fun resolveTimeStamp(timeStamp: Long): Long = if (timeStamp > 0) timeStamp else System.currentTimeMillis()

/** @return fileId if the file has it, or -1 if the file has no fileId */
private fun fileIdOf(file: VirtualFile): Int = if (file is VirtualFileWithId) file.id else -1

@Internal
abstract class AbstractContentWriteTask(
  val fileId: Int,
  val file: VirtualFile,
  val requestor: Any?,
  val modStamp: Long,
  timeStamp: Long,
  /**
   * Is timeStamp update explicitly requested by the API caller -- or it is an internal matter of VFS async write impl, to
   * ensure timestamps consistency with the write while it was in pending state?
   */
  val timeStampUpdateRequestedByCaller: Boolean = (timeStamp > 0),
  val content: ByteArray,
  val contentLength: Int,
) : FileIOTaskExecutor.FileIOTask {

  val timeStamp: Long = resolveTimeStamp(timeStamp)

  override fun fileId(): Int = fileId

  override fun execute(executedOnBackground: Boolean) {
    val block = {
      write()

      try {
        updateLastModifiedTimeOfUnderlyingFile(timeStamp)
      }
      catch (e: IOException) {
        //It could be we have permissions to write the file content but don't have permissions to update the file `mtime`.
        // The error processing in this case depends on _why_ we wanted to update `mtime`:
        // a) if this was explicitly requested in an API call like `.getOutputStream(..., timestamp)` => we should rethrow an
        //    exception, since we can't fulfill the request
        // b) if updating `mtime` is an internal matter of async VFS write implementation, to keep file timestamp consistent
        //    with 'pending' state -- better not to rethrow an exception, and just update the VFS file timestamp from the
        //    actual file timestamp -- which may create a significant jump in VFS timestamp, but it is less of an evil.
        if (timeStampUpdateRequestedByCaller) {
          throw e
        }
        else {
          LOG.warn("[${file.path}][#$fileId]: lastModified update failed -> re-read actual timestamp to update VFS timestamp", e)
        }
      }

      updateLastModifiedTimestampInVFS()
    }

    if (executedOnBackground) {
      //Cancellation does no good if we're already running on a background, without stalling the main thread -- but
      // cancellation support (e.g. DiskQueryRelay, if fileSystem uses it -- which it should) overhead is non-zero,
      // => better disable it:
      Cancellation.executeInNonCancelableSection(block)
    }
    else {
      block()
    }
  }

  private fun updateLastModifiedTimestampInVFS() {
    //Underlying FS could have different lastModified granularity than currentTimeMillis has => re-query
    //the lastModified just set, to know how the FS actually stores it:
    val modificationTimeStampWithUnderlyingFSGranularity = lastModifiedTimeOfUnderlyingFile()
    if (modificationTimeStampWithUnderlyingFSGranularity != timeStamp) {
      //hack: call low-level API directly, because normal file.setTimestamp() causes infinite recursion & stack overflow:
      val vfs = FSRecords.getInstance()
      vfs.connection().records().updateRecord(fileId){
        record -> record.setTimestamp(modificationTimeStampWithUnderlyingFSGranularity)
      }
    }
  }

  /** Should write `content[0..contentLength)` into a file, but SHOULD NOT try updating the file's lastModified to timeStamp */
  protected abstract fun write()

  /** @return should return the lastModified timestamp of an actual (underlying, physical) file */
  protected abstract fun lastModifiedTimeOfUnderlyingFile(): Long

  /** Should set the lastModified attribute of an actual (underlying, physical) file */
  @Throws(IOException::class)
  protected abstract fun updateLastModifiedTimeOfUnderlyingFile(lastModified: Long)

  override fun isAsyncExecutionAllowed(): Boolean = ASYNC_CONTENT_WRITE_ENABLED && requestor is AsyncFileContentWriteRequestor

  override fun toString(): String =
    "ContentWriteTask[#$fileId][requestor: ${requestor?.javaClass?.name}]" +
    "[modStamp=$modStamp, timeStamp=$timeStamp, contentLength=$contentLength, timeStampUpdateRequestedByCaller=$timeStampUpdateRequestedByCaller]"
}

/** Limits both: (# of tasks postponed) AND (total size of memory used by postponed tasks) */
@Internal
class BackpressureBySizeAndTaskCount(
  private val maxTasksCountBeforeBackpressure: Int,
  private val maxSizeBeforeBackpressure: Long,
) : AsyncableFileIOTaskExecutor.BackpressureStrategy<AbstractContentWriteTask> {

  private var pendingTasksCount: Int = 0
  private var pendingMemorySize: Long = 0

  override fun entering(task: AbstractContentWriteTask): Boolean {
    pendingTasksCount += 1
    //count for memory pressure: real size of byte[], not .contentLength which is 'logical' size
    pendingMemorySize += task.contentLength

    return pendingTasksCount > maxTasksCountBeforeBackpressure
           || pendingMemorySize > maxSizeBeforeBackpressure
  }

  override fun exiting(task: AbstractContentWriteTask) {
    pendingTasksCount -= 1
    pendingMemorySize -= task.contentLength
  }

  override fun toString(): String =
    "BackpressureBySizeAndTaskCount" +
    "[pendingTasks: $pendingTasksCount vs $maxTasksCountBeforeBackpressure max]" +
    "[pendingSize: $pendingMemorySize vs $maxSizeBeforeBackpressure max]"
}
