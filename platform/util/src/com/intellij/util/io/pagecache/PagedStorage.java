// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.openapi.Forceable;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 *
 */
public interface PagedStorage extends Forceable, AutoCloseable {
  @NotNull StorageLockContext getStorageLockContext();

  @NotNull Path getFile();

  boolean isReadOnly();

  int getPageSize();

  boolean isNativeBytesOrder();

  void putInt(final long offsetInFile,
              final int value) throws IOException;

  int getInt(final long offsetInFile) throws IOException;

  void putLong(final long offsetInFile,
               final long value) throws IOException;

  long getLong(final long offsetInFile) throws IOException;

  void putBuffer(final long offsetInFile,
                 final @NotNull ByteBuffer data) throws IOException;

  byte get(final long offsetInFile) throws IOException;

  void put(final long offsetInFile,
           final byte value) throws IOException;

  void get(final long offsetInFile,
           final byte[] destination, final int offsetInArray, final int length) throws IOException;

  void put(final long offsetInFile,
           final byte[] src, final int offsetInArray, final int length) throws IOException;

  /**
   * Length of data in this file. Length increasing only as we write to the file -- i.e. if one
   * requests a page ahead of the current length, this doesn't lead to length increasing. Only
   * when one writes something on that page storage length is also increasing.
   */
  long length();

  void clear();

  @NotNull Page pageByOffset(final long offsetInFile,
                             final boolean forModification) throws IOException;

  int toOffsetInPage(final long offsetInFile);

  boolean isClosed();

  @Override
  boolean isDirty();

  @Override
  void force() throws IOException;

  @Override
  void close() throws IOException, InterruptedException;
}
