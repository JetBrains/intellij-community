// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.*
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path

object PersistentFSRecordsStorageFactory {
  private val PERSISTENT_FS_STORAGE_CONTEXT_RW = StorageLockContext(true, true, true)

  enum class RecordsStorageKind {
    REGULAR,
    IN_MEMORY,
    OVER_LOCK_FREE_FILE_CACHE,
    OVER_MMAPPED_FILE
  }

  @JvmField
  val RECORDS_STORAGE_KIND =
    RecordsStorageKind.valueOf(System.getProperty("vfs.records-storage-impl", RecordsStorageKind.REGULAR.name));


  @JvmStatic
  fun recordsLength(): Int =
    when (RECORDS_STORAGE_KIND) {
      RecordsStorageKind.REGULAR, RecordsStorageKind.IN_MEMORY -> PersistentFSSynchronizedRecordsStorage.RECORD_SIZE
      RecordsStorageKind.OVER_LOCK_FREE_FILE_CACHE -> PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES
      RecordsStorageKind.OVER_MMAPPED_FILE -> PersistentFSRecordsLockFreeOverMMappedFile.RECORD_SIZE_IN_BYTES
    }

  @JvmStatic
  @Throws(IOException::class)
  fun createStorage(file: Path): PersistentFSRecordsStorage {
    FSRecords.LOG.info("using $RECORDS_STORAGE_KIND storage for VFS records")

    return when (RECORDS_STORAGE_KIND) {
      RecordsStorageKind.REGULAR -> PersistentFSSynchronizedRecordsStorage(openRMappedFile(file, recordsLength()))
      RecordsStorageKind.IN_MEMORY -> PersistentInMemoryFSRecordsStorage(file,  /*max size: */1 shl 24)
      RecordsStorageKind.OVER_LOCK_FREE_FILE_CACHE -> createLockFreeStorage(file)
      RecordsStorageKind.OVER_MMAPPED_FILE -> PersistentFSRecordsLockFreeOverMMappedFile(file,
                                                                                         PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE)
    }
  }

  @JvmStatic
  @VisibleForTesting
  @Throws(IOException::class)
  fun openRMappedFile(file: Path,
                      recordLength: Int): ResizeableMappedFile {
    val pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE * recordLength / PersistentFSSynchronizedRecordsStorage.RECORD_SIZE

    val aligned = pageSize % recordLength == 0
    if (!aligned) {
      val message = "Record length(=$recordLength) is not aligned with page size(=$pageSize)"
      Logger.getInstance(PersistentFSRecordsStorage::class.java).error(message)
    }

    return ResizeableMappedFile(file, recordLength * 1024,
                                PERSISTENT_FS_STORAGE_CONTEXT_RW,
                                pageSize,
                                aligned,
                                IOUtil.useNativeByteOrderForByteBuffers())
  }

  @JvmStatic
  @VisibleForTesting
  @Throws(IOException::class)
  fun createLockFreeStorage(file: Path): PersistentFSRecordsOverLockFreePagedStorage {
    val recordLength = PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES
    val pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE

    if (!PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
      throw AssertionError(
        "Bug: PageCacheUtils.LOCK_FREE_VFS_ENABLED=false " +
        "=> can't create PersistentFSRecordsOverLockFreePagedStorage is FilePageCacheLockFree is disabled")
    }

    val recordsArePageAligned = pageSize % recordLength == 0
    if (!recordsArePageAligned) {
      throw AssertionError("Bug: record length(=$recordLength) is not aligned with page size(=$pageSize)")
    }

    val storage = PagedFileStorageLockFree(
      file,
      PERSISTENT_FS_STORAGE_CONTEXT_RW,
      pageSize,
      IOUtil.useNativeByteOrderForByteBuffers()
    )
    return PersistentFSRecordsOverLockFreePagedStorage(storage)
  }
}