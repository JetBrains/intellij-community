// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.Forceable;

import java.io.Closeable;
import java.io.IOException;

/**
 * FIXME type something meaningful here
 */
public interface IDataTable extends Closeable, Forceable {
  boolean isCompactNecessary();

  void readBytes(long address, byte[] bytes) throws IOException;

  void writeBytes(long address, byte[] bytes) throws IOException;

  void writeBytes(long address, byte[] bytes, int off, int len) throws IOException;

  long allocateSpace(int len) throws IOException;

  void reclaimSpace(int len) throws IOException;

  @Override
  void close() throws IOException;

  @Override
  void force() throws IOException;

  @Override
  boolean isDirty();

  int getWaste();

  long getFileSize();
}
