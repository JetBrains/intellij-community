// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@ApiStatus.Internal
public final class ExtendibleHashMapStorageOverMMappedFile implements ExtendibleHashMapStorage {
  private final @NotNull MMappedFileStorage storage;
  private final int segmentSize;

  public ExtendibleHashMapStorageOverMMappedFile(@NotNull MMappedFileStorage storage,
                                                 int segmentSize) {
    if (Integer.bitCount(segmentSize) != 1) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be a power of 2");
    }
    var pageSize = storage.pageSize();
    if (segmentSize > pageSize) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be <= pageSize(=" + pageSize + ")");
    }
    if (pageSize % segmentSize != 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must align with pageSize(=" + pageSize + ")");
    }
    this.storage = storage;
    this.segmentSize = segmentSize;
  }

  @Override
  public boolean isEmpty() throws IOException {
    return storage.actualFileSize() == 0;
  }

  @Override
  public int segmentSize() {
    return segmentSize;
  }

  @Override
  public @NotNull MemorySegment segment(int segmentIndex) throws IOException {
    if (segmentIndex < 0) {
      throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be >= 0");
    }
    var offsetInFile = segmentIndex * (long)segmentSize;
    var page = storage.pageByOffset(offsetInFile);
    var offsetInPage = storage.toOffsetInPage(offsetInFile);
    return page.rawPageSegment().asSlice(offsetInPage, segmentSize);
  }

  @Override
  public @NotNull MemorySegment allocateSegment(int segmentIndex) throws IOException {
    return segment(segmentIndex);
  }

  @Override
  public void clear() throws IOException {
    storage.zeroizeTillEOF(/*startingOffset: */0L);
  }

  @Override
  public boolean isOpen() {
    return storage.isOpen();
  }

  @Override
  public void flush() throws IOException {
    storage.flush();
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public void closeAndClean() throws IOException {
    storage.closeAndClean();
  }

  @Override
  public String toString() {
    return storage.toString();
  }
}
