// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.FilePageCacheLockFree;

import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Page: region of file, 'mapped' into memory. Part of our own file page cache
 * implementation, {@link com.intellij.util.io.PagedFileStorageLockFree}
 * <p>
 *
 * @see FilePageCacheLockFree
 */
public interface Page extends AutoCloseable, Flushable {
  int pageSize();

  int pageIndex();

  long offsetInFile();

  long lastOffsetInFile();

  void lockPageForWrite();

  void unlockPageForWrite();

  void lockPageForRead();

  void unlockPageForRead();

  boolean isUsable();

  void release();

  /** == {@link #release()} */
  @Override
  void close();

  boolean isDirty();

  @Override
  void flush() throws IOException;

  <OUT, E extends Exception> OUT read(final int startOffset,
                                      final int length,
                                      final ThrowableNotNullFunction<ByteBuffer, OUT, E> reader) throws E;

  <OUT, E extends Exception> OUT write(final int startOffset,
                                       final int length,
                                       final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E;

  byte get(final int offsetInPage);

  int getInt(final int offsetInPage);

  long getLong(final int offsetInPage);

  void readToArray(final byte[] destination,
                   final int offsetInArray,
                   final int offsetInPage,
                   final int length);

  void put(final int offsetInPage,
           final byte value);

  void putInt(final int offsetInPage,
              final int value);

  void putLong(final int offsetInPage,
               final long value);

  void putFromBuffer(final ByteBuffer data,
                     final int offsetInPage);

  void putFromArray(final byte[] source,
                    final int offsetInArray,
                    final int offsetInPage,
                    final int length);

  /**
   * More detailed page description than {@link #toString()}.
   * Exists only for debug purposes
   */
  String formatData();
}
