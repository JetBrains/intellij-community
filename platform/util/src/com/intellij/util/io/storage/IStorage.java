// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;

public interface IStorage extends Disposable, Forceable, CleanableStorage {
  int getVersion() throws IOException;

  void setVersion(int expectedVersion) throws IOException;

  int getLiveRecordsCount() throws IOException;

  RecordIdIterator createRecordIdIterator() throws IOException;

  IStorageDataOutput writeStream(int record);

  IStorageDataOutput writeStream(int record, boolean fixedSize);

  IAppenderStream appendStream(int record);

  DataInputStream readStream(int record) throws IOException;

  void writeBytes(int record, @NotNull ByteArraySequence bytes, boolean fixedSize) throws IOException;

  void checkSanity(int record) throws IOException;

  void replaceBytes(int record, int offset, @NotNull ByteArraySequence bytes) throws IOException;
}
