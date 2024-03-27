// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.util.SystemProperties
import com.intellij.util.io.*
import com.intellij.util.io.dev.StorageFactory
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

@Internal
abstract class PersistentFSRecordsStorageFactory(val id: Int) : StorageFactory<PersistentFSRecordsStorage> {


  /** Currently the default impl */
  data class OverMMappedFile(val pageSize: Int = PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE,
                             val acquireStorageOwnership: Boolean = true)
    : PersistentFSRecordsStorageFactory(id = 0) {

    override fun open(storagePath: Path): PersistentFSRecordsLockFreeOverMMappedFile = MMappedFileStorageFactory.withDefaults()
      .pageSize(pageSize)
      .wrapStorageSafely<PersistentFSRecordsLockFreeOverMMappedFile, IOException>(storagePath) { mappedFileStorage ->
        val recordsStorage = PersistentFSRecordsLockFreeOverMMappedFile(mappedFileStorage)

        if (acquireStorageOwnership) {
          val currentProcessPid = ProcessHandle.current().pid().toInt()
          val acquiredByPid = recordsStorage.tryAcquireExclusiveAccess(currentProcessPid, /*forcibly: */ false)
          if (acquiredByPid != currentProcessPid) {
            val ownerProcess = ProcessHandle.of(acquiredByPid.toLong()).getOrNull()
            if (ownerProcess != null && ownerProcess.isAlive) {
              //OSes re-use process ids, so false positives (pid collision) are possible.
              //But this branch is reached only after improper shutdown, and we can't 100% guarantee VFS recovery after such
              // a shutdowns anyway. So pid collision is just another case of bad luck -- it won't be a recovery, but a VFS
              // rebuild.
              //MAYBE RC: we could do a bit better by checking that an ownerProcess runs a JVM -- this should greatly
              //          reduce collision probability. But how to do that?
              //          1. Check ownerProcess.info().command()=='java': not reliable, it could be 'idea', or something else.
              //          2. Check .command() == thisProcess.command(): not reliable, it could any _other_ JB app process using
              //             this process's VFS -- this is exactly the scenario we're trying to catch with that 'storage ownership'
              //             check
              //          3. Check .commandLine().contains('JetBrains'): better, since most of our tools has 'JetBrains' either in
              //             exec path, or somewhere in classpath -- but again, it leaves out rare cases there VFS is acquired by
              //             something _unusual_
              //          4. Timestamp: store not only the pid, but also a _timestamp_ of VFS acquisition -- compare this timestamp
              //             with ownerProcess.startInstant. If ownerProcess happened to be started _after_ VFS was acquired -- it
              //             is definitely a false positive, just a pid collision: original process that really acquired VFS has
              //             already died, and it's pid is now re-used by a new process. This seems to be the most reliable option,
              //             but it needs additional development (i.e. additional header field in records to store timestamp)

              val message = "Records storage [$storagePath] is in use by another process [pid: $acquiredByPid, info: ${ownerProcess.info()}]"
              val failIfUsedByAnotherProcess = SystemProperties.getBooleanProperty("vfs.fail-if-used-by-another-process", true)
              if (failIfUsedByAnotherProcess) {
                throw StorageAlreadyInUseException(message)
              }
              else {
                FSRecords.LOG.error(message)
              }
            }
            else {
              FSRecords.LOG.warn("Records storage [$storagePath] was in use by process [pid: $acquiredByPid] which is not exist now (wasn't closed properly/crashed?) -> re-acquiring forcibly")
            }

            //Previous owner process is terminated, i.e. storage wasn't closed properly on an app termination (crash?)
            // => acquire storage forcibly
            val acquiredByPidForcibly = recordsStorage.tryAcquireExclusiveAccess(currentProcessPid, /*forcibly: */ true)
            if (acquiredByPidForcibly != currentProcessPid) {
              //yet another process acquired records concurrently => too suspicious, just fail:
              val concurrentlyOwnedProcess = ProcessHandle.of(acquiredByPidForcibly.toLong()).getOrNull()
              throw StorageAlreadyInUseException("Records storage [$storagePath] is in use by another process [pid: $acquiredByPidForcibly, info: ${concurrentlyOwnedProcess?.info()}]")
            }
          }
        }

        recordsStorage
      }
  }

  /** Fallback impl for [OverMMappedFile] if something goes terribly wrong, and we can't fix it quickly */
  data class OverLockFreeFileCache(val pageSize: Int = PageCacheUtils.DEFAULT_PAGE_SIZE) : PersistentFSRecordsStorageFactory(id = 1) {
    private val PERSISTENT_FS_STORAGE_CONTEXT_RW = StorageLockContext(true, true, true)

    init {
      val recordLength = PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES
      val recordsArePageAligned = pageSize % recordLength == 0
      if (!recordsArePageAligned) {
        throw AssertionError("Bug: record length(=$recordLength) is not aligned with page size(=$pageSize)")
      }
    }

    override fun open(storagePath: Path): PersistentFSRecordsOverLockFreePagedStorage {

      if (!PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
        throw IOException(
          "Configuration mismatch: PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED=false " +
          "=> can't create PersistentFSRecordsOverLockFreePagedStorage if FilePageCacheLockFree is disabled")
      }

      val pagedStorage = PagedFileStorageWithRWLockedPageContent(
        storagePath,
        PERSISTENT_FS_STORAGE_CONTEXT_RW,
        pageSize,
        IOUtil.useNativeByteOrderForByteBuffers(),
        PageContentLockingStrategy.LOCK_PER_PAGE
      )
      return IOUtil.wrapSafely<PersistentFSRecordsOverLockFreePagedStorage, PagedFileStorageWithRWLockedPageContent, IOException>(
        pagedStorage) {
        PersistentFSRecordsOverLockFreePagedStorage(it)
      }
    }
  }

  /** For testing/benchmarking: serves as a reference point. Not a prod-level implementation! */
  data class InMemory(val maxRecordsCount: Int = (1 shl 24)) : PersistentFSRecordsStorageFactory(id = 2) {
    @Suppress("TestOnlyProblems")
    override fun open(storagePath: Path) = PersistentFSRecordsOverInMemoryStorage(storagePath, maxRecordsCount)
  }

  companion object {
    private var storageFactory = defaultFromSystemProperties()


    @JvmStatic
    fun storageImplementation(): PersistentFSRecordsStorageFactory = storageFactory

    @VisibleForTesting
    @JvmStatic
    @JvmName("setStorageImplementation")
    fun setStorageImplementation(value: PersistentFSRecordsStorageFactory) {
      storageFactory = value
    }

    @VisibleForTesting
    @JvmStatic
    @JvmName("resetStorageImplementation")
    fun resetStorageImplementation() {
      storageFactory = defaultFromSystemProperties()
    }

    private fun defaultFromSystemProperties(): PersistentFSRecordsStorageFactory {
      return when (System.getProperty("vfs.records-storage.impl", "OVER_MMAPPED_FILE")) {
        "OVER_LOCK_FREE_FILE_CACHE" -> OverLockFreeFileCache()
        "IN_MEMORY" -> InMemory()
        else -> OverMMappedFile()
      }
    }
  }
}