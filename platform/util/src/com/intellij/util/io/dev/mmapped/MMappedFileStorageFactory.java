// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.mmapped;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.RegionAllocationAtomicityLock;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.RegionAllocationAtomicityLock.Region;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;


@ApiStatus.Internal
public class MMappedFileStorageFactory implements StorageFactory<MMappedFileStorage> {

  public static final int DEFAULT_PAGE_SIZE = IOUtil.MiB;

  public static MMappedFileStorageFactory withDefaults() {
    return new MMappedFileStorageFactory(DEFAULT_PAGE_SIZE, false, true);
  }


  private final int pageSize;

  /**
   * What to do if fileSize is not page-aligned (i.e. fileSize != N*pageSize), and there is no marker of unfinished
   * file expansion?
   * true: expand (and fill with zeroes) the file, so it is page-aligned (fileSize=N * pageSize)
   * false: throw exception
   * Useful mostly useful for something like backward compatibility: e.g. increase pageSize is a safe change for many
   * storages -- there is no migration needed, except to align the file size to the new pageSize.
   */
  private final boolean expandFileIfNotPageAligned;

  /** If directories along the path to the file do not exist yet -- create them */
  private final boolean createParentDirectoriesIfNotExist;

  private MMappedFileStorageFactory(int pageSize,
                                    boolean expandFileIfNotPageAligned,
                                    boolean createParentDirectoriesIfNotExist) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    this.pageSize = pageSize;
    this.expandFileIfNotPageAligned = expandFileIfNotPageAligned;
    this.createParentDirectoriesIfNotExist = createParentDirectoriesIfNotExist;
  }

  public MMappedFileStorageFactory pageSize(int pageSize) {
    return new MMappedFileStorageFactory(pageSize, expandFileIfNotPageAligned, createParentDirectoriesIfNotExist);
  }

  /**
   * What to do if fileSize is not page-aligned (i.e. fileSize != N*pageSize), and there is no marker of unfinished
   * file expansion?
   * true: expand (and fill with zeroes) the file, so it is page-aligned (fileSize=N * pageSize)
   * false: throw IOException
   */
  public MMappedFileStorageFactory expandFileIfNotPageAligned(boolean expand) {
    return new MMappedFileStorageFactory(pageSize, expand, createParentDirectoriesIfNotExist);
  }

  /**
   * true (default): create parent directory(ies) if missed
   * false: throw {@link NoSuchFileException} if parent directory doesn't exist
   */
  public MMappedFileStorageFactory createParentDirectories(boolean createParentDirectories) {
    return new MMappedFileStorageFactory(pageSize, expandFileIfNotPageAligned, createParentDirectories);
  }

  @Override
  public @NotNull MMappedFileStorage open(@NotNull Path storagePath) throws IOException {
    Path parentDir = storagePath.getParent().toAbsolutePath();
    if (!Files.exists(parentDir)) {
      if (createParentDirectoriesIfNotExist) {
        Files.createDirectories(parentDir);
      }
      else {
        throw new NoSuchFileException(
          "Parent directory of [" + storagePath.toAbsolutePath() + "] is not exist, and .createDirectoriesIfNotExist=false");
      }
    }

    RegionAllocationAtomicityLock regionAllocationLock = RegionAllocationAtomicityLock.defaultLock(storagePath);

    long fileSize = Files.exists(storagePath) ? Files.size(storagePath) : 0;
    if (fileSize % pageSize != 0) {
      //file size is not page-aligned: suspicious

      //Maybe there was a file expansion interrupted by app crash/kill?
      long startOfSuspiciousRegion = (fileSize / pageSize) * pageSize;
      Region region = regionAllocationLock.region(startOfSuspiciousRegion, pageSize);
      if (!region.isUnfinished()) {
        // It is generally an error to have file un-aligned with page size, so fail if not explicitly asked to ignore:
        if (!expandFileIfNotPageAligned) {
          throw new IOException("[" + storagePath + "]: fileSize(=" + fileSize + " b) is not page(=" + pageSize + " b)-aligned");
        }

        //else: expand (zeroes) file up to the next page.

        //This branch is useful for something like backward compatibility: e.g. increase pageSize is a safe change for many
        // storages -- there is no migration needed, except to align the file size to the new pageSize.
        long fileSizeRoundedUpToPageSize = (fileSize / pageSize) + 1;
        try (FileChannel channel = FileChannel.open(storagePath, WRITE)) {
          IOUtil.allocateFileRegion(channel, fileSizeRoundedUpToPageSize);
        }
      }
      else {
        //There is 'unfinished' region -- i.e. file expansion and zeroing started, but was interrupted by app crash/kill.
        // MMappedFileStorage will deal with it, no need to do anything here
      }
    }

    return new MMappedFileStorage(storagePath, pageSize, regionAllocationLock);
  }

  @Override
  public String toString() {
    return "MMappedFileStorageFactory{" +
           "pageSize: " + pageSize +
           ", expandFileIfNotPageAligned: " + expandFileIfNotPageAligned +
           ", createParentDirectoriesIfNotExist: " + createParentDirectoriesIfNotExist +
           '}';
  }
}
