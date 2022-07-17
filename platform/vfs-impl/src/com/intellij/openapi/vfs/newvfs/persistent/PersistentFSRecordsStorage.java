// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.SystemProperties;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
abstract class PersistentFSRecordsStorage {
  static boolean useLockFreeRecordsStorage = SystemProperties.getBooleanProperty("idea.use.lock.free.record.storage.for.vfs", false);

  static int recordsLength() {
    return useLockFreeRecordsStorage ? PersistentFSLockFreeRecordsStorage.RECORD_SIZE : PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;
  }

  static PersistentFSRecordsStorage createStorage(@NotNull ResizeableMappedFile file) throws IOException {
    return useLockFreeRecordsStorage ? new PersistentFSLockFreeRecordsStorage(file) : new PersistentFSSynchronizedRecordsStorage(file);
  }

  abstract int allocateRecord();

  abstract void setAttributeRecordId(int fileId, int recordId) throws IOException;

  abstract int getAttributeRecordId(int fileId) throws IOException;

  abstract int getParent(int fileId) throws IOException;

  abstract void setParent(int fileIf, int parentId) throws IOException;

  abstract int getNameId(int fileId) throws IOException;

  abstract void setNameId(int fileId, int nameId) throws IOException;

  abstract void setFlags(int fileId, int flags) throws IOException;

  abstract long getLength(int fileId) throws IOException;

  abstract void putLength(int fileId, long length) throws IOException;

  abstract long getTimestamp(int fileId) throws IOException;

  abstract void putTimestamp(int fileId, long timestamp) throws IOException;

  abstract int getModCount(int fileId) throws IOException;

  abstract void setModCount(int fileId, int counter) throws IOException;

  abstract int getContentRecordId(int fileId) throws IOException;

  abstract void setContentRecordId(int fileId, int recordId) throws IOException;

  abstract int getFlags(int fileId) throws IOException;

  abstract void setAttributesAndIncModCount(int fileId, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteMissed) throws IOException;

  abstract boolean isDirty();

  abstract long getTimestamp() throws IOException;

  abstract void setConnectionStatus(int code) throws IOException;

  abstract int getConnectionStatus() throws IOException;

  abstract void setVersion(int version) throws IOException;

  abstract int getVersion() throws IOException;

  abstract int getGlobalModCount();

  abstract int incGlobalModCount();

  abstract long length();

  abstract void cleanRecord(int fileId) throws IOException;

  @SuppressWarnings("UnusedReturnValue")
  abstract boolean processAllNames(@NotNull NameFlagsProcessor processor) throws IOException;

  abstract void force() throws IOException;

  abstract void close() throws IOException;

  @FunctionalInterface
  interface NameFlagsProcessor {
    void process(int fileId, int nameId, int flags);
  }
}
