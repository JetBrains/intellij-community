// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class PersistentFSRecordsStorage {
  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = 4;
  private static final int CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_SIZE = 4;
  private static final int TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MOD_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MOD_COUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int LENGTH_SIZE = 8;

  static final int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;
  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  @NotNull
  private final ResizeableMappedFile myFile;

  public PersistentFSRecordsStorage(@NotNull ResizeableMappedFile file) {
    myFile = file;
  }

  int getGlobalModCount() {
    return myFile.getInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET);
  }

  int incGlobalModCount() {
    final int count = getGlobalModCount() + 1;
    myFile.putInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET, count);
    return count;
  }

  long getTimestamp() {
    return myFile.getLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET);
  }

  void setVersion(int version) {
    myFile.putInt(PersistentFSHeaders.HEADER_VERSION_OFFSET, version);
    myFile.putLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
  }

  int getVersion() {
    return myFile.getInt(PersistentFSHeaders.HEADER_VERSION_OFFSET);
  }

  void setConnectionStatus(int connectionStatus) {
    myFile.putInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
  }

  int getConnectionStatus() {
    return myFile.getInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET);
  }

  int getNameId(int id) {
    assert id > 0 : id;
    return getRecordInt(id, NAME_OFFSET);
  }

  void setNameId(int id, int nameId) {
    PersistentFSConnection.ensureIdIsValid(nameId);
    putRecordInt(id, NAME_OFFSET, nameId);
  }

  int getParent(int id) {
    return getRecordInt(id, PARENT_OFFSET);
  }

  void setParent(int id, int parent) {
    putRecordInt(id, PARENT_OFFSET, parent);
  }

  int getModCount(int id) {
    return getRecordInt(id, MOD_COUNT_OFFSET);
  }

  @PersistentFS.Attributes
  int doGetFlags(int id) {
    return getRecordInt(id, FLAGS_OFFSET);
  }

  void setFlags(int id, @PersistentFS.Attributes int flags) {
    putRecordInt(id, FLAGS_OFFSET, flags);
  }

  void setModCount(int id, int value) {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  int getContentRecordId(int fileId) {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  void setContentRecordId(int id, int value) {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  int getAttributeRecordId(int id) {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  void setAttributeRecordId(int id, int value) {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  long getTimestamp(int id) {
    return myFile.getLong(getOffset(id, TIMESTAMP_OFFSET));
  }

  boolean putTimeStamp(int id, long value) {
    int timeStampOffset = getOffset(id, TIMESTAMP_OFFSET);
    if (myFile.getLong(timeStampOffset) != value) {
      myFile.putLong(timeStampOffset, value);
      return true;
    }
    return false;
  }

  long getLength(int id) {
    return myFile.getLong(getOffset(id, LENGTH_OFFSET));
  }

  boolean putLength(int id, long value) {
    int lengthOffset = getOffset(id, LENGTH_OFFSET);
    if (myFile.getLong(lengthOffset) != value) {
      myFile.putLong(lengthOffset, value);
      return true;
    }
    return false;
  }

  void cleanRecord(int id) {
    myFile.put(((long)id) * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
  }

  private int getRecordInt(int id, int offset) {
    return myFile.getInt(getOffset(id, offset));
  }

  private void putRecordInt(int id, int offset, int value) {
    myFile.putInt(getOffset(id, offset), value);
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  long length() {
    return myFile.length();
  }

  void close() {
    try {
      myFile.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void force() {
    try {
      myFile.force();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  boolean isDirty() {
    return myFile.isDirty();
  }
}
