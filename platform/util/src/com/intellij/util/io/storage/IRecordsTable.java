// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.Forceable;
import org.jetbrains.annotations.TestOnly;

import java.io.Closeable;
import java.io.IOException;

public interface IRecordsTable extends Closeable, Forceable {
  int createNewRecord() throws IOException;

  int getRecordsCount() throws IOException;

  RecordIdIterator createRecordIdIterator() throws IOException;

  @TestOnly
  int getLiveRecordsCount() throws IOException;

  long getAddress(int record) throws IOException;

  void setAddress(int record, long address) throws IOException;

  int getSize(int record) throws IOException;

  void setSize(int record, int size) throws IOException;

  int getCapacity(int record) throws IOException;

  void setCapacity(int record, int capacity) throws IOException;

  void deleteRecord(int record) throws IOException;

  int getVersion() throws IOException;

  void setVersion(int expectedVersion) throws IOException;

  @Override
  void close() throws IOException;

  @Override
  void force() throws IOException;

  @Override
  boolean isDirty();

  void markDirty() throws IOException;
}
