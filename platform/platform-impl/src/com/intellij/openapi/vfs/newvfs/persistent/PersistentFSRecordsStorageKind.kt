// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.util.io.IOUtil
import com.intellij.util.io.PageCacheUtils
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.dev.StorageFactory
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path

enum class PersistentFSRecordsStorageKind : StorageFactory<PersistentFSRecordsStorage> {

  /** Currently the default impl */
  OVER_MMAPPED_FILE {

    override fun open(storagePath: Path): PersistentFSRecordsLockFreeOverMMappedFile = MMappedFileStorageFactory.withDefaults()
      .pageSize(PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE)
      .wrapStorageSafely<PersistentFSRecordsLockFreeOverMMappedFile, IOException>(storagePath) { storage ->
        PersistentFSRecordsLockFreeOverMMappedFile(storage)
      }
  },

  /** Fallback impl for [OVER_MMAPPED_FILE] if something goes terribly wrong, and we can't fix it quickly */
  OVER_LOCK_FREE_FILE_CACHE {
    private val PERSISTENT_FS_STORAGE_CONTEXT_RW = StorageLockContext(true, true, true)

    override fun open(storagePath: Path): PersistentFSRecordsOverLockFreePagedStorage {
      val recordLength = PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES
      val pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE

      if (!PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
        throw IOException(
          "Configuration mismatch: PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED=false " +
          "=> can't create PersistentFSRecordsOverLockFreePagedStorage if FilePageCacheLockFree is disabled")
      }

      val recordsArePageAligned = pageSize % recordLength == 0
      if (!recordsArePageAligned) {
        throw AssertionError("Bug: record length(=$recordLength) is not aligned with page size(=$pageSize)")
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
  },

  /** For testing/benchmarking: serves as a reference point. Not a prod-level implementation! */
  IN_MEMORY {
    override fun open(storagePath: Path) =
      @Suppress("TestOnlyProblems") (PersistentInMemoryFSRecordsStorage(storagePath,  /*max size: */1 shl 24))
  };

  companion object {
    private var RECORDS_STORAGE_KIND = defaultFromSystemProperties()


    @JvmStatic
    fun storageImplementation(): PersistentFSRecordsStorageKind = RECORDS_STORAGE_KIND

    @VisibleForTesting
    @JvmStatic
    @JvmName("setStorageImplementation")
    fun setStorageImplementation(value: PersistentFSRecordsStorageKind) {
      RECORDS_STORAGE_KIND = value
    }

    @VisibleForTesting
    @JvmStatic
    @JvmName("resetStorageImplementation")
    fun resetStorageImplementation() {
      RECORDS_STORAGE_KIND = defaultFromSystemProperties()
    }

    private fun defaultFromSystemProperties() = PersistentFSRecordsStorageKind.valueOf(System.getProperty("vfs.records-storage.impl", OVER_MMAPPED_FILE.name))
  }
}