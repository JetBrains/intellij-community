// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.ResizeableMappedFile;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
abstract class PersistentFSRecordsStorage {
  private static final StorageLockContext PERSISTENT_FS_STORAGE_CONTEXT_RW = new StorageLockContext(true, true, true);

  static boolean useLockFreeRecordsStorage = SystemProperties.getBooleanProperty("idea.use.lock.free.record.storage.for.vfs", false);

  static int recordsLength() {
    return useLockFreeRecordsStorage ? PersistentFSLockFreeRecordsStorage.RECORD_SIZE : PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;
  }

  static PersistentFSRecordsStorage createStorage(@NotNull Path file) throws IOException {
    ResizeableMappedFile resizeableMappedFile = createFile(file, recordsLength());

    FSRecords.LOG.info("using " + (useLockFreeRecordsStorage ? "synchronized" : "lock-free") + " storage for VFS records");

    return useLockFreeRecordsStorage
           ? new PersistentFSLockFreeRecordsStorage(resizeableMappedFile)
           : new PersistentFSSynchronizedRecordsStorage(resizeableMappedFile);
  }

  @VisibleForTesting
  static @NotNull ResizeableMappedFile createFile(@NotNull Path file, int recordLength) throws IOException {
    int pageSize = PagedFileStorage.BUFFER_SIZE * recordLength / PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;

    boolean aligned = pageSize % recordLength == 0;
    if (!aligned) {
      String message = "Buffer size " + PagedFileStorage.BUFFER_SIZE + " is not aligned for record size " + recordLength;
      Logger.getInstance(PersistentFSRecordsStorage.class).error(message);
    }

    return new ResizeableMappedFile(file, recordLength * 1024,
                                    PERSISTENT_FS_STORAGE_CONTEXT_RW,
                                    pageSize,
                                    aligned,
                                    IOUtil.useNativeByteOrderForByteBuffers());
  }

  /**
   * @return id of newly allocated record
   */
  abstract int allocateRecord();

  //TODO RC: offset constant named ATTR_REF, but accessor is attributeRecord -- which one is correct, REFERENCE or RECORD?
  abstract void setAttributeRecordId(int fileId, int recordId) throws IOException;

  abstract int getAttributeRecordId(int fileId) throws IOException;

  abstract int getParent(int fileId) throws IOException;

  abstract void setParent(int fileId, int parentId) throws IOException;

  abstract int getNameId(int fileId) throws IOException;

  abstract void setNameId(int fileId, int nameId) throws IOException;

  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  abstract boolean setFlags(int fileId, int flags) throws IOException;

  abstract long getLength(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  //RC: why 'put' not 'set'?
  abstract boolean putLength(int fileId, long length) throws IOException;

  abstract long getTimestamp(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  //RC: why 'put' not 'set'?
  abstract boolean putTimestamp(int fileId, long timestamp) throws IOException;

  abstract int getModCount(int fileId) throws IOException;

  abstract void setModCount(int fileId, int counter) throws IOException;

  abstract int getContentRecordId(int fileId) throws IOException;

  abstract void setContentRecordId(int fileId, int recordId) throws IOException;

  abstract @PersistentFS.Attributes int getFlags(int fileId) throws IOException;

  //TODO RC: what semantics is assumed for the method in concurrent context? If it is 'update atomically' than
  //         it makes it harder to implement a storage in a lock-free way
  //FIXME RC: method name setAttributesAndIncModCount, but no implementation really increments modCount!
  abstract void setAttributesAndIncModCount(int fileId,
                                            long timestamp,
                                            long length,
                                            int flags,
                                            int nameId,
                                            int parentId,
                                            boolean overwriteMissed) throws IOException;

  abstract void cleanRecord(int fileId) throws IOException;

  /* ======================== STORAGE HEADER ============================================================================== */

  abstract long getTimestamp() throws IOException;

  abstract void setConnectionStatus(int code) throws IOException;

  abstract int getConnectionStatus() throws IOException;

  abstract void setVersion(int version) throws IOException;

  abstract int getVersion() throws IOException;

  abstract int getGlobalModCount();

  abstract int incGlobalModCount();

  /**
   * @return length of underlying file storage, in bytes
   */
  abstract long length();

  abstract boolean isDirty();

  // TODO add a synchronization or requirement to be called on the loading
  @SuppressWarnings("UnusedReturnValue")
  abstract boolean processAllRecords(@NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException;

  abstract void force() throws IOException;

  abstract void close() throws IOException;

  @FunctionalInterface
  interface FsRecordProcessor {
    void process(int fileId, int nameId, int flags, int parentId, boolean corrupted);
  }
}
