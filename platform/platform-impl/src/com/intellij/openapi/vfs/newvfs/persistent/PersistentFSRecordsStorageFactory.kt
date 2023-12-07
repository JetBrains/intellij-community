// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.io.*
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists


object PersistentFSRecordsStorageFactory {
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
  @JvmStatic
  private val FAIL_EARLY_IF_LEGACY_STORAGE_DETECTED: Boolean = getBooleanProperty("vfs.fail-early-if-legacy-storage-detected", false)

  private val PERSISTENT_FS_STORAGE_CONTEXT_RW = StorageLockContext(true, true, true)

  enum class RecordsStorageKind {
    /** Currently the default impl */
    OVER_MMAPPED_FILE,
    /** Fallback impl for [OVER_MMAPPED_FILE] if something goes terribly wrong, and we can't fix it quickly */
    OVER_LOCK_FREE_FILE_CACHE,
    /** For testing/benchmarking: serves as a reference point. Not a prod-level implementation! */
    IN_MEMORY
  }

  private var RECORDS_STORAGE_KIND = RecordsStorageKind.valueOf(
    System.getProperty("vfs.records-storage.impl", RecordsStorageKind.OVER_MMAPPED_FILE.name))


  @JvmStatic
  fun getRecordsStorageImplementation(): RecordsStorageKind = RECORDS_STORAGE_KIND

  @VisibleForTesting
  @JvmStatic
  @JvmName("setRecordsStorageImplementation")
  fun setRecordsStorageImplementation(value: RecordsStorageKind) {
    RECORDS_STORAGE_KIND = value
  }

  @VisibleForTesting
  @JvmStatic
  @JvmName("resetRecordsStorageImplementation")
  fun resetRecordsStorageImplementation() {
    RECORDS_STORAGE_KIND = RecordsStorageKind.valueOf(System.getProperty("vfs.records-storage.impl", RecordsStorageKind.OVER_MMAPPED_FILE.name))
  }


  @JvmStatic
  @Throws(IOException::class)
  fun createStorage(file: Path): PersistentFSRecordsStorage {
    FSRecords.LOG.trace("using $RECORDS_STORAGE_KIND storage for VFS records")

    return when (RECORDS_STORAGE_KIND) {
      RecordsStorageKind.IN_MEMORY -> @Suppress("TestOnlyProblems") PersistentInMemoryFSRecordsStorage(file,  /*max size: */1 shl 24)
      RecordsStorageKind.OVER_LOCK_FREE_FILE_CACHE -> createLockFreeStorage(file)
      RecordsStorageKind.OVER_MMAPPED_FILE -> {
        //TODO RC: this should be replaced with/encapsulated into StorageFactory<PersistentFSStorage>
        if (FAIL_EARLY_IF_LEGACY_STORAGE_DETECTED) {
          val legacyLengthFile = file.resolveSibling(file.fileName.toString() + ".len")
          if (legacyLengthFile.exists()) {
            //MAYBE RC: actually, here we can _migrate_ legacy file to the new format: move the files to the tmp folder,
            // create empty new storage, and just copy all records from legacy storage to the new one...
            throw IOException("Legacy records file detected (${legacyLengthFile} exists): VFS rebuild required")
          }
        }
        PersistentFSRecordsLockFreeOverMMappedFile(
          file,
          PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE
        )
      }
    }
  }

  @JvmStatic
  @VisibleForTesting
  @Throws(IOException::class)
  fun openRMappedFile(file: Path,
                      recordLength: Int): ResizeableMappedFile {
    val pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE * recordLength / PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES

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

    if (!PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
      throw AssertionError(
        "Bug: PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED=false " +
        "=> can't create PersistentFSRecordsOverLockFreePagedStorage if FilePageCacheLockFree is disabled")
    }

    val recordsArePageAligned = pageSize % recordLength == 0
    if (!recordsArePageAligned) {
      throw AssertionError("Bug: record length(=$recordLength) is not aligned with page size(=$pageSize)")
    }

    val storage = PagedFileStorageWithRWLockedPageContent(
      file,
      PERSISTENT_FS_STORAGE_CONTEXT_RW,
      pageSize,
      IOUtil.useNativeByteOrderForByteBuffers(),
      PageContentLockingStrategy.LOCK_PER_PAGE
    )
    try {
      return PersistentFSRecordsOverLockFreePagedStorage(storage)
    }
    catch (t: Throwable) {
      storage.close()
      throw t
    }
  }
}