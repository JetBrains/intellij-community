// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public abstract class PersistentFSRecordsStorage {
  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT_RW = new StorageLockContext(true, true, true);

  enum RecordsStorageKind {
    REGULAR,
    LOCK_FREE,
    IN_MEMORY,

    OVER_LOCK_FREE_FILE_CACHE,
    OVER_MMAPPED_FILE
  }

  static final RecordsStorageKind
    RECORDS_STORAGE_KIND = RecordsStorageKind.valueOf(System.getProperty("idea.records-storage-kind", RecordsStorageKind.REGULAR.name()));

  static int recordsLength() {
    return switch (RECORDS_STORAGE_KIND) {
      case REGULAR, IN_MEMORY -> PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;
      case LOCK_FREE -> PersistentFSLockFreeRecordsStorage.RECORD_SIZE;
      case OVER_LOCK_FREE_FILE_CACHE -> PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES;
      case OVER_MMAPPED_FILE -> PersistentFSRecordsLockFreeOverMMappedFile.RECORD_SIZE_IN_BYTES;
    };
  }

  static PersistentFSRecordsStorage createStorage(final @NotNull Path file) throws IOException {
    FSRecords.LOG.info("using " + RECORDS_STORAGE_KIND + " storage for VFS records");

    return switch (RECORDS_STORAGE_KIND) {
      case REGULAR -> new PersistentFSSynchronizedRecordsStorage(openRMappedFile(file, recordsLength()));
      case LOCK_FREE -> new PersistentFSLockFreeRecordsStorage(openRMappedFile(file, recordsLength()));
      case IN_MEMORY -> new PersistentInMemoryFSRecordsStorage(file, /*max size: */1 << 24);

      case OVER_LOCK_FREE_FILE_CACHE -> createLockFreeStorage(file);
      case OVER_MMAPPED_FILE ->
        new PersistentFSRecordsLockFreeOverMMappedFile(file, PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE);
    };
  }

  @VisibleForTesting
  static @NotNull ResizeableMappedFile openRMappedFile(final @NotNull Path file,
                                                       final int recordLength) throws IOException {
    int pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE * recordLength / PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;

    boolean aligned = pageSize % recordLength == 0;
    if (!aligned) {
      String message = "Record length(=" + recordLength + ") is not aligned with page size(=" + pageSize + ")";
      Logger.getInstance(PersistentFSRecordsStorage.class).error(message);
    }

    return new ResizeableMappedFile(file, recordLength * 1024,
                                    PERSISTENT_FS_STORAGE_CONTEXT_RW,
                                    pageSize,
                                    aligned,
                                    IOUtil.useNativeByteOrderForByteBuffers());
  }

  @VisibleForTesting
  static @NotNull PersistentFSRecordsOverLockFreePagedStorage createLockFreeStorage(final @NotNull Path file) throws IOException {
    final int recordLength = PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES;
    final int pageSize = PageCacheUtils.DEFAULT_PAGE_SIZE;

    if (!PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
      throw new AssertionError(
        "Bug: PageCacheUtils.LOCK_FREE_VFS_ENABLED=false " +
        "=> can't create PersistentFSRecordsOverLockFreePagedStorage is FilePageCacheLockFree is disabled");
    }

    final boolean recordsArePageAligned = pageSize % recordLength == 0;
    if (!recordsArePageAligned) {
      throw new AssertionError("Bug: record length(=" + recordLength + ") is not aligned with page size(=" + pageSize + ")");
    }

    final PagedFileStorageLockFree storage = new PagedFileStorageLockFree(
      file,
      PERSISTENT_FS_STORAGE_CONTEXT_RW,
      pageSize,
      IOUtil.useNativeByteOrderForByteBuffers()
    );
    return new PersistentFSRecordsOverLockFreePagedStorage(storage);
  }


  /**
   * @return id of newly allocated record
   */
  public abstract int allocateRecord();

  public abstract void setAttributeRecordId(int fileId, int recordId) throws IOException;

  public abstract int getAttributeRecordId(int fileId) throws IOException;

  public abstract int getParent(int fileId) throws IOException;

  public abstract void setParent(int fileId, int parentId) throws IOException;

  public abstract int getNameId(int fileId) throws IOException;

  public abstract void setNameId(int fileId, int nameId) throws IOException;

  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  public abstract boolean setFlags(int fileId, int flags) throws IOException;

  //TODO RC: boolean updateFlags(fileId, int maskBits, boolean riseOrClean)

  public abstract long getLength(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  public abstract boolean setLength(int fileId, long length) throws IOException;

  public abstract long getTimestamp(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  public abstract boolean setTimestamp(int fileId, long timestamp) throws IOException;

  public abstract int getModCount(int fileId) throws IOException;

  //TODO RC: why we need this method? Record modification is detected by actual modification -- there
  //         are (seems to) no way to modify record bypassing it.
  //         We use the method to mark file record modified there something derived is modified -- e.g.
  //         children attribute or content. This looks suspicious to me: why we need to update _file_
  //         record version in those cases?
  public abstract void markRecordAsModified(int fileId) throws IOException;

  public abstract int getContentRecordId(int fileId) throws IOException;

  public abstract boolean setContentRecordId(int fileId, int recordId) throws IOException;

  public abstract @PersistentFS.Attributes int getFlags(int fileId) throws IOException;

  //TODO RC: what semantics is assumed for the method in concurrent context? If it is 'update atomically' than
  //         it makes it harder to implement a storage in a lock-free way

  /**
   * Fills all record fields in one shot
   */
  public abstract void fillRecord(int fileId,
                                  long timestamp,
                                  long length,
                                  int flags,
                                  int nameId,
                                  int parentId,
                                  boolean overwriteAttrRef) throws IOException;

  public abstract void cleanRecord(int fileId) throws IOException;

  /* ======================== STORAGE HEADER ============================================================================== */

  public abstract long getTimestamp() throws IOException;

  public abstract void setConnectionStatus(int code) throws IOException;

  public abstract int getConnectionStatus() throws IOException;

  public abstract void setVersion(int version) throws IOException;

  public abstract int getVersion() throws IOException;

  public abstract int getGlobalModCount();

  /**
   * @return length of underlying file storage, in bytes
   */
  public abstract long length();

  public abstract boolean isDirty();

  // TODO add a synchronization or requirement to be called on the loading
  @SuppressWarnings("UnusedReturnValue")
  public abstract boolean processAllRecords(@NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException;

  public abstract void force() throws IOException;

  public abstract void close() throws IOException;

  @FunctionalInterface
  interface FsRecordProcessor {
    void process(int fileId, int nameId, int flags, int parentId, boolean corrupted);
  }
}
