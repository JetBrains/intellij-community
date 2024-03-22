// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.util.SystemProperties
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
import kotlin.io.path.exists

enum class PersistentFSRecordsStorageKind : StorageFactory<PersistentFSRecordsStorage> {

  /** Currently the default impl */
  OVER_MMAPPED_FILE {
    /**
     * Fallback option: usually [PersistentFSConnector] deals with storages version changes just fine.
     * But there are cases where it fails: e.g. with mmapped storages on Win, there file once mapped is hard
     * to remove (and [PersistentFSConnector] relies on ability to just remove legacy storages files).
     * In such cases, this option could help get rid of the legacy file early, before it is mapped
     * MAYBE RC: we probably outgrow that issues:
     *           a) it's been 2 releases since we moved to memory-mapped for fs-records, i.e. everybody who could have this
     *           problem -- already has fixed it
     *           b) i've done a lot for memory-mapped files to work properly on Windows also
     */
    private val FAIL_EARLY_IF_LEGACY_STORAGE_DETECTED: Boolean = SystemProperties.getBooleanProperty(
      "vfs.fail-early-if-legacy-storage-detected", false)

    override fun open(storagePath: Path): PersistentFSRecordsLockFreeOverMMappedFile {
      //TODO RC: this should be replaced with/encapsulated into StorageFactory<PersistentFSStorage>
      if (FAIL_EARLY_IF_LEGACY_STORAGE_DETECTED) {
        val legacyLengthFile = storagePath.resolveSibling(storagePath.fileName.toString() + ".len")
        if (legacyLengthFile.exists()) {
          //MAYBE RC: actually, here we can _migrate_ legacy file to the new format: move the files to the tmp folder,
          // create empty new storage, and just copy all records from legacy storage to the new one...
          throw IOException("Legacy records file detected (${legacyLengthFile} exists): VFS rebuild required")
        }
      }
      return MMappedFileStorageFactory.withDefaults()
        .pageSize(PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE)
        .wrapStorageSafely<PersistentFSRecordsLockFreeOverMMappedFile, IOException>(storagePath) { storage ->
          PersistentFSRecordsLockFreeOverMMappedFile(storage)
        }
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