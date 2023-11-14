// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.CleanableStorage;
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

  int createNewRecord() throws IOException;

  DataInputStream readStream(int recordId) throws IOException;

  void writeBytes(int recordId, @NotNull ByteArraySequence bytes, boolean fixedSize) throws IOException;

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
