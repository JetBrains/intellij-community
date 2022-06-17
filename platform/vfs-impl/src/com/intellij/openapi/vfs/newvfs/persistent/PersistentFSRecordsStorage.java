// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.SystemProperties;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
interface PersistentFSRecordsStorage {
  boolean useLockFreeRecordsStorage = SystemProperties.getBooleanProperty("idea.use.lock.free.record.storage.for.vfs", false);

  static int recordsLength() {
    return useLockFreeRecordsStorage ? PersistentFSLockFreeRecordsStorage.RECORD_SIZE : PersistentFSSynchronizedRecordsStorage.RECORD_SIZE;
  }

  static PersistentFSRecordsStorage createStorage(@NotNull ResizeableMappedFile file) throws IOException {
    return useLockFreeRecordsStorage ? new PersistentFSLockFreeRecordsStorage(file) : new PersistentFSSynchronizedRecordsStorage(file);
  }

  int allocateRecord();

  void setAttributeRecordId(int fileId, int recordId) throws IOException;

  int getAttributeRecordId(int fileId) throws IOException;

  int getParent(int fileId) throws IOException;

  void setParent(int fileIf, int parentId) throws IOException;

  int getNameId(int fileId) throws IOException;

  void setNameId(int fileId, int nameId) throws IOException;

  void setFlags(int fileId, int flags) throws IOException;

  long getLength(int fileId) throws IOException;

  void putLength(int fileId, long length) throws IOException;

  long getTimestamp(int fileId) throws IOException;

  void putTimestamp(int fileId, long timestamp) throws IOException;

  int getModCount(int fileId) throws IOException;

  void setModCount(int fileId, int counter) throws IOException;

  int getContentRecordId(int fileId) throws IOException;

  void setContentRecordId(int fileId, int recordId) throws IOException;

  int getFlags(int fileId) throws IOException;

  void setAttributesAndIncModCount(int fileId, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteMissed) throws IOException;

  boolean isDirty();

  long getTimestamp() throws IOException;

  void setConnectionStatus(int code) throws IOException;

  int getConnectionStatus() throws IOException;

  void setVersion(int version) throws IOException;

  int getVersion() throws IOException;

  int getGlobalModCount();

  int incGlobalModCount();

  long length();

  void cleanRecord(int fileId) throws IOException;

  boolean processAllNames(@NotNull NameFlagsProcessor processor) throws IOException;

  void force() throws IOException;

  void close() throws IOException;

  interface NameFlagsProcessor {
    void process(int fileId, int nameId, int flags);
  }
}
