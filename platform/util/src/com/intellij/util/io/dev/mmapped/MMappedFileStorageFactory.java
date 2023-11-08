// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.mmapped;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;


@ApiStatus.Internal
public class MMappedFileStorageFactory implements StorageFactory<MMappedFileStorage> {

  public static final int DEFAULT_PAGE_SIZE = IOUtil.MiB;

  public static MMappedFileStorageFactory withDefaults() {
    return new MMappedFileStorageFactory(DEFAULT_PAGE_SIZE, false);
  }


  private final int pageSize;
  /**
   * What to do if fileSize is not page-aligned (i.e. fileSize != N*pageSize)?
   * true: expand (and fill with zeroes) the file, so it is page-aligned (fileSize=N * pageSize)
   * false: throw exception
   */
  private final boolean expandFileIfNotPageAligned;

  private MMappedFileStorageFactory(int pageSize,
                                    boolean expandFileIfNotPageAligned) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    this.pageSize = pageSize;
    this.expandFileIfNotPageAligned = expandFileIfNotPageAligned;
  }

  public MMappedFileStorageFactory pageSize(int pageSize) {
    return new MMappedFileStorageFactory(pageSize, false);
  }

  /**
   * What to do if fileSize is not page-aligned (i.e. fileSize != N*pageSize)?
   * true: expand (and fill with zeroes) the file, so it is page-aligned (fileSize=N * pageSize)
   * false: throw IOException
   */
  public MMappedFileStorageFactory expandFileIfNotPageAligned(boolean expand) {
    return new MMappedFileStorageFactory(pageSize, expand);
  }

  @Override
  public @NotNull MMappedFileStorage open(@NotNull Path storagePath) throws IOException {
    long fileSize = Files.exists(storagePath) ? Files.size(storagePath) : 0;
    if (fileSize % pageSize != 0) {
      if (!expandFileIfNotPageAligned) {
        throw new IOException("[" + storagePath + "]: fileSize(=" + fileSize + " b) is not page(=" + pageSize + " b)-aligned");
      }
      //else:expand (zeroes) file up to next page
      long fileSizeRoundedUpToPageSize = (fileSize / pageSize) + 1;
      try (FileChannel channel = FileChannel.open(storagePath, WRITE)) {
        IOUtil.allocateFileRegion(channel, fileSizeRoundedUpToPageSize);
      }
    }

    return new MMappedFileStorage(storagePath, pageSize);
  }
}
