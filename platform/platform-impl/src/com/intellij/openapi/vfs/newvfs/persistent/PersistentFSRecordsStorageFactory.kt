// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.util.io.IOUtil
import com.intellij.util.io.PageCacheUtils
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.dev.StorageFactory
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path

@Internal
abstract class PersistentFSRecordsStorageFactory(val id: Int) : StorageFactory<PersistentFSRecordsStorage> {


  /** Currently the default impl */
  data class OverMMappedFile(val pageSize: Int = PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE)
    : PersistentFSRecordsStorageFactory(id = 0) {

    override fun open(storagePath: Path): PersistentFSRecordsLockFreeOverMMappedFile = MMappedFileStorageFactory.withDefaults()
      .pageSize(pageSize)
      .wrapStorageSafely<PersistentFSRecordsLockFreeOverMMappedFile, IOException>(storagePath) { mappedFileStorage ->
        PersistentFSRecordsLockFreeOverMMappedFile(mappedFileStorage)
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
    override fun open(storagePath: Path) = PersistentInMemoryFSRecordsStorage(storagePath, maxRecordsCount)
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