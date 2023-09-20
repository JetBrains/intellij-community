// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.mmapped;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;


@ApiStatus.Internal
public class MMappedFileStorageFactory implements StorageFactory<MMappedFileStorage> {

  public static final int DEFAULT_PAGE_SIZE = IOUtil.MiB;

  public static final MMappedFileStorageFactory DEFAULT = new MMappedFileStorageFactory(DEFAULT_PAGE_SIZE);

  public static MMappedFileStorageFactory withPageSize(int pageSize) {
    return DEFAULT.pageSize(pageSize);
  }

  private final int pageSize;

  private MMappedFileStorageFactory(int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    this.pageSize = pageSize;
  }

  public MMappedFileStorageFactory pageSize(int pageSize) {
    return new MMappedFileStorageFactory(pageSize);
  }

  @Override
  public @NotNull MMappedFileStorage open(@NotNull Path storagePath) throws IOException {
    //TODO mapCurrentFile?
    //final long length = Files.exists(path) ? Files.size(path) : 0;
    //
    //final int pagesToMapExistingFileContent = (int)((length % pageSize == 0) ?
    //                                                (length / pageSize) :
    //                                                ((length / pageSize) + 1));
    //if (pagesToMapExistingFileContent > initialPagesCount) {
    //  throw new IllegalStateException(
    //    "Storage size(" + length + " b) > maxFileSize(" + initialPagesToMap + " b): " +
    //    "file [" + path + "] is corrupted?");
    //}

    return new MMappedFileStorage(storagePath, pageSize);
  }
}
