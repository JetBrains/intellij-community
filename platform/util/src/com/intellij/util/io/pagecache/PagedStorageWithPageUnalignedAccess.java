// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.util.io.Bits;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Wrapper for {@link PagedFileStorageWithRWLockedPageContent} which allows page-unaligned
 * accesses: i.e. read int which is 2 bytes on one page, and 2 bytes on the next page.
 */
public final class PagedStorageWithPageUnalignedAccess implements PagedStorage {

  private final @NotNull PagedStorage alignedAccessStorage;

  /** {@code alignedAccessStorage.getPageSize()}, cached for faster access */
  private final transient int pageSize;

  public PagedStorageWithPageUnalignedAccess(final @NotNull PagedStorage storage) {
    alignedAccessStorage = storage;
    pageSize = alignedAccessStorage.getPageSize();
  }


  private boolean isPageAligned(final long offsetInFile,
                                final int valueLength) {
    final int offsetInPage = (int)(offsetInFile % pageSize);
    final int remainsOnPage = pageSize - offsetInPage;
    return valueLength <= remainsOnPage;
  }

  private static final ThreadLocal<byte[]> TL_BUFFER_FOR_PRIMITIVES = ThreadLocal.withInitial(() -> new byte[Long.BYTES]);

  private static byte[] threadLocalBufferForPrimitives() {
    return TL_BUFFER_FOR_PRIMITIVES.get();
  }


  @Override
  public void putInt(final long offsetInFile,
                     final int value) throws IOException {
    if (isPageAligned(offsetInFile, Integer.BYTES)) {
      alignedAccessStorage.putInt(offsetInFile, value);
    }
    else {
      final byte[] bufferForPrimitives = threadLocalBufferForPrimitives();
      Bits.putInt(bufferForPrimitives, 0, value);
      put(offsetInFile, bufferForPrimitives, 0, Integer.BYTES);
    }
  }

  @Override
  public int getInt(final long offsetInFile) throws IOException {
    if (isPageAligned(offsetInFile, Integer.BYTES)) {
      return alignedAccessStorage.getInt(offsetInFile);
    }
    else {
      final byte[] bufferForPrimitives = threadLocalBufferForPrimitives();
      get(offsetInFile, bufferForPrimitives, 0, Integer.BYTES);
      return Bits.getInt(bufferForPrimitives, 0);
    }
  }

  @Override
  public void putLong(final long offsetInFile,
                      final long value) throws IOException {
    if (isPageAligned(offsetInFile, Long.BYTES)) {
      alignedAccessStorage.putLong(offsetInFile, value);
    }
    else {
      final byte[] bufferForPrimitives = threadLocalBufferForPrimitives();
      Bits.putLong(bufferForPrimitives, 0, value);
      put(offsetInFile, bufferForPrimitives, 0, Long.BYTES);
    }
  }

  @Override
  public long getLong(final long offsetInFile) throws IOException {
    if (isPageAligned(offsetInFile, Long.BYTES)) {
      return alignedAccessStorage.getLong(offsetInFile);
    }
    else {
      final byte[] bufferForPrimitives = threadLocalBufferForPrimitives();
      get(offsetInFile, bufferForPrimitives, 0, Long.BYTES);
      return Bits.getLong(bufferForPrimitives, 0);
    }
  }

  @Override
  public void putBuffer(final long offsetInFile,
                        final @NotNull ByteBuffer data) throws IOException {
    if (!isPageAligned(offsetInFile, data.remaining())) {
      //TODO implement unaligned putBuffer
      throw new UnsupportedOperationException(".putBuffer() is not (yet?) implemented for unaligned storage");
    }
    else {
      alignedAccessStorage.putBuffer(offsetInFile, data);
    }
  }


  // ============= methods below are pure delegates: ============================================ //

  @Override
  public byte get(final long offsetInFile) throws IOException {
    return alignedAccessStorage.get(offsetInFile);
  }

  @Override
  public void put(final long offsetInFile,
                  final byte value) throws IOException {
    alignedAccessStorage.put(offsetInFile, value);
  }

  @Override
  public void get(final long offsetInFile,
                  final byte[] destination, final int offsetInArray, final int length) throws IOException {
    alignedAccessStorage.get(offsetInFile, destination, offsetInArray, length);
  }

  @Override
  public void put(final long offsetInFile,
                  final byte[] src, final int offsetInArray, final int length) throws IOException {
    alignedAccessStorage.put(offsetInFile, src, offsetInArray, length);
  }

  @Override
  public @NotNull Path getFile() {
    return alignedAccessStorage.getFile();
  }

  @Override
  public boolean isReadOnly() {
    return alignedAccessStorage.isReadOnly();
  }

  @Override
  public int getPageSize() {
    return alignedAccessStorage.getPageSize();
  }

  @Override
  public boolean isNativeBytesOrder() {
    return alignedAccessStorage.isNativeBytesOrder();
  }

  @Override
  public long length() {
    return alignedAccessStorage.length();
  }

  @Override
  public void clear() {
    alignedAccessStorage.clear();
  }

  @Override
  public boolean isDirty() {
    return alignedAccessStorage.isDirty();
  }

  @Override
  public @NotNull Page pageByOffset(final long offsetInFile,
                                    final boolean forModification) throws IOException {
    return alignedAccessStorage.pageByOffset(offsetInFile, forModification);
  }

  @Override
  public int toOffsetInPage(final long offsetInFile) {
    return alignedAccessStorage.toOffsetInPage(offsetInFile);
  }

  @Override
  public boolean isClosed() {
    return alignedAccessStorage.isClosed();
  }

  @Override
  public void force() throws IOException {
    alignedAccessStorage.force();
  }

  @Override
  public void close() throws IOException {
    alignedAccessStorage.close();
  }
}
