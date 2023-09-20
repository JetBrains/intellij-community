// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public class ExtendibleMapFactory implements StorageFactory<ExtendibleHashMap> {
  private final int pageSize;
  private final int segmentSize;

  private ExtendibleMapFactory(int pageSize,
                               int segmentSize) {
    if (Integer.bitCount(segmentSize) != 1) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be power of 2");
    }
    if (segmentSize > pageSize) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be <= pageSize(=" + pageSize + ")");
    }
    if ((pageSize % segmentSize) != 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must align with pageSize(=" + pageSize + ")");
    }
    this.pageSize = pageSize;
    this.segmentSize = segmentSize;
  }

  public static ExtendibleMapFactory defaults() {
    //approx. 16M (key,value) pairs
    return new ExtendibleMapFactory(
      ExtendibleHashMap.DEFAULT_STORAGE_PAGE_SIZE,
      ExtendibleHashMap.DEFAULT_SEGMENT_SIZE
    );
  }

  public static ExtendibleMapFactory large() {
    //approx. 64M (key,value) pairs
    int segmentSize = ExtendibleHashMap.DEFAULT_SEGMENT_SIZE * 2;
    int pageSize = segmentSize * ExtendibleHashMap.DEFAULT_SEGMENTS_PER_PAGE;
    return new ExtendibleMapFactory(pageSize, segmentSize);
  }

  public ExtendibleMapFactory pageSize(int pageSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize);
  }

  public ExtendibleMapFactory segmentSize(int segmentSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize);
  }

  @Override
  public @NotNull ExtendibleHashMap open(@NotNull Path storagePath) throws IOException {
    //int maxFileSize = segmentSize / 2 * segmentSize;
    return MMappedFileStorageFactory.DEFAULT
      .pageSize(pageSize)
      .wrapStorageSafely(
        storagePath,
        storage -> new ExtendibleHashMap(storage, segmentSize)
      );
  }
}
