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
import java.io.InputStream;

/**
 * VFS storage for file content.
 * Provides content deduplication: same content (bytes) stored >1 times actually stored only once, and id of already
 * existing record returned
 */
public interface VFSContentStorage extends CleanableStorage, Closeable, Forceable {

  int getVersion() throws IOException;

  void setVersion(int expectedVersion) throws IOException;

  /**
   * Stores bytes and return contentRecordId, by which content could be later retrieved.
   * If the same bytes was already stored -- method could return id of already existing record, without allocating
   * & storing new record.
   */
  int storeRecord(@NotNull ByteArraySequence bytes) throws IOException;

  void checkRecord(int recordId, boolean fastCheck) throws IOException;

  InputStream readStream(int recordId) throws IOException;

  /**
   * This method is only to support legacy usage.
   * Shouldn't be any new usage: now content hashes is an implementation detail, hidden behind storage abstraction
   */
  @ApiStatus.Obsolete
  byte[] contentHash(int recordId) throws IOException;

  //MAYBE RC: replace it with .forEach()-style iteration method
  RecordIdIterator createRecordIdIterator() throws IOException;

  boolean isEmpty() throws IOException;

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
