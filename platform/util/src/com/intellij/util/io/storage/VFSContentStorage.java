// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;

/**
 *
 */
public interface VFSContentStorage extends CleanableStorage, Closeable, Forceable {

  int getVersion() throws IOException;

  void setVersion(int expectedVersion) throws IOException;

  /**
   * Stores bytes and return contentRecordId, by which content could be later retrieved.
   * If the same bytes was already stored -- method could return id of already existing record, without allocating
   * & storing new record.
   */
  int storeRecord(@NotNull ByteArraySequence bytes, boolean fixedSize) throws IOException;

  void checkRecord(int recordId, boolean fastCheck) throws IOException;

  DataInputStream readStream(int recordId) throws IOException;

  /**
   * This method is only to support legacy usage.
   * Shouldn't be any new usage: now content hashes is an implementation detail, hidden behind storage abstraction
   */
  @ApiStatus.Obsolete
  byte[] contentHash(int recordId) throws IOException;

  RecordIdIterator createRecordIdIterator() throws IOException;

  int getRecordsCount() throws IOException;


  @Override
  boolean isDirty();

  @Override
  void force() throws IOException;

  @Override
  void close() throws IOException;

  @Override
  void closeAndClean() throws IOException;
}
