// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;

import java.nio.ByteBuffer;

/**
 * Page: region of file, 'mapped' into memory. Part of our own file page cache
 * implementation, {@link PagedFileStorageWithRWLockedPageContent}
 * <p>
 *
 * @see FilePageCacheLockFree
 */
public interface Page extends AutoCloseable {
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

  //===== page content access: ================================================================================
  //RC: There are several different ways to access/modify page content:
  //    Lambda-based .read() and .write() methods: I plan them to be the default option for
  //    accessing page content -- this is the safest way, since all locking, page state checking,
  //    buffer range checking/slicing, etc. is done once, under the cover and can't be messed up.
  //    But they have some overhead in terms of raw performance.
  //
  //    Methods like .getXXX(), .putXXX(): are supposed to be used in the least performance-critical
  //    places. They are here mostly for compatibility with previous PFCache implementation, to ease
  //    transition. Their performance overhead is comparable with lambda-based methods, but only
  //    one slot is accessed at a time, so there is less room to amortize the overhead.
  //
  //    Methods .rawPageBuffer() and .regionModified(), together with .lockForXXX()/.unlockForXXX():
  //    it is the 'lower-level API' -- no lambda overhead, no buffer slicing overhead, but you need
  //    to be very careful to put all important pieces in the right places. I.e. if you forget to call
  //    .regionModified() then your changes will be still available while the page is in cache,
  //    but will be lost as soon as the page is evicted and re-loaded later (good luck debugging).
  //
  //    There is also a possibility to (almost) lock-free access: with VarHandle on the top of
  //    .rawPageBuffer() you could use CAS/volatile on the buffer content, and avoid using 
  //    .lockForXXX()/.unlockForXXX() methods. (There is still a lock in .regionModified(), but
  //    it is very narrow so shouldn't bother you until very, very high contention). This is the
  //    lowest-level method to access page content: it could give the best performance and scalability,
  //    but at the cost of very high complexity and quite high probability of bugs.

  <OUT, E extends Exception> OUT read(final int startOffsetOnPage,
                                      final int length,
                                      final ThrowableNotNullFunction<ByteBuffer, OUT, E> reader) throws E;

  <OUT, E extends Exception> OUT write(final int startOffsetOnPage,
                                       final int length,
                                       final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E;

  //=============================================================================================


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
