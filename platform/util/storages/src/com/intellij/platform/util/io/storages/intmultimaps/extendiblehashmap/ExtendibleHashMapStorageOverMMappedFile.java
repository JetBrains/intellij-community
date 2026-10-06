// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

//Binary layout: (header) (segmentsTable) (data segment)+
@ApiStatus.Internal
public final class ExtendibleHashMapStorageOverMMappedFile implements ExtendibleHashMapStorage {
  private final @NotNull MMappedFileStorage storage;
  private final int headerSize;
  private final int segmentSize;
  private boolean headerAccessed;

  public ExtendibleHashMapStorageOverMMappedFile(@NotNull MMappedFileStorage storage,
                                                 int headerSize,
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
    if (headerSize <= 0 || headerSize > segmentSize) {
      throw new IllegalArgumentException("headerSize(=" + headerSize + ") must be in [1.." + segmentSize + "]");
    }
    this.storage = storage;
    this.headerSize = headerSize;
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
  public int headerSize() {
    return headerSize;
  }

  @Override
  public @NotNull MemorySegment header() throws IOException {
    long fileSize = storage.actualFileSize();
    if (fileSize > 0 && fileSize < segmentSize) {
      throw new CorruptedException("Hashmap storage (=" + fileSize + "b) contains an incomplete header (" + segmentSize + "b)");
    }
    var page = storage.pageByOffset(0);
    headerAccessed = true;
    return page.rawPageSegment().asSlice(0, headerSize);
  }

  @Override
  public @NotNull MemorySegment segmentsTable() throws IOException {
    ensureHeaderAccessed();
    return segmentByOffset(segmentSize);
  }

  @Override
  public @NotNull MemorySegment segment(int segmentIndex) throws IOException {
    ensureHeaderAccessed();
    if (segmentIndex < 0) {
      throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must be >= 0");
    }
    return segmentByOffset((segmentIndex + 2L) * segmentSize);
  }

  private @NotNull MemorySegment segmentByOffset(long offsetInFile) throws IOException {
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
    headerAccessed = false;
  }

  private void ensureHeaderAccessed() {
    if (!headerAccessed) {
      throw new IllegalStateException("Call header() before accessing the segments table or hash map segments");
    }
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
