// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.mmapped;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage.RegionAllocationAtomicityLock;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory.IfNotPageAligned.THROW_EXCEPTION;
import static java.nio.file.StandardOpenOption.WRITE;


@ApiStatus.Internal
public class MMappedFileStorageFactory implements StorageFactory<MMappedFileStorage> {
  private static final Logger LOG = Logger.getInstance(MMappedFileStorageFactory.class);

  public static final int DEFAULT_PAGE_SIZE = IOUtil.MiB;

  public static MMappedFileStorageFactory withDefaults() {
    return new MMappedFileStorageFactory(DEFAULT_PAGE_SIZE, THROW_EXCEPTION, true);
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
  private final IfNotPageAligned ifFileNotPageAligned;

  /** If directories along the path to the file do not exist yet -- create them */
  private final boolean createParentDirectoriesIfNotExist;

  private MMappedFileStorageFactory(int pageSize,
                                    @NotNull IfNotPageAligned ifFileNotPageAligned,
                                    boolean createParentDirectoriesIfNotExist) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    this.pageSize = pageSize;
    this.ifFileNotPageAligned = ifFileNotPageAligned;
    this.createParentDirectoriesIfNotExist = createParentDirectoriesIfNotExist;
  }

  public MMappedFileStorageFactory pageSize(int pageSize) {
    return new MMappedFileStorageFactory(pageSize, ifFileNotPageAligned, createParentDirectoriesIfNotExist);
  }

  /**
   * What to do if fileSize is not page-aligned (i.e. fileSize != N*pageSize), and there is no marker of unfinished
   * file expansion?
   * {@link IfNotPageAligned#EXPAND_FILE} (and fill with zeroes) the file, so it is page-aligned (fileSize=N * pageSize)
   * {@link IfNotPageAligned#THROW_EXCEPTION}: throw IOException, {@link IfNotPageAligned#CLEAN} drop the current file
   * content, and open the file as-new
   */
  public MMappedFileStorageFactory ifFileIsNotPageAligned(@NotNull IfNotPageAligned ifFileNotPageAligned) {
    return new MMappedFileStorageFactory(pageSize, ifFileNotPageAligned, createParentDirectoriesIfNotExist);
  }

  /**
   * true (default): create parent directory(ies) if missed
   * false: throw {@link NoSuchFileException} if parent directory doesn't exist
   */
  public MMappedFileStorageFactory createParentDirectories(boolean createParentDirectories) {
    return new MMappedFileStorageFactory(pageSize, ifFileNotPageAligned, createParentDirectories);
  }

  @Override
  public @NotNull MMappedFileStorage open(@NotNull Path storagePath) throws IOException {
    Path absoluteStoragePath = storagePath.toAbsolutePath();

    boolean storageFileExists = Files.exists(absoluteStoragePath);

    if (!storageFileExists) {
      checkParentDirectories(absoluteStoragePath);
    }//if the storage file does exist => parentDir definitely does exist also

    RegionAllocationAtomicityLock regionAllocationLock = RegionAllocationAtomicityLock.defaultLock(absoluteStoragePath);

    long fileSize = storageFileExists ? Files.size(absoluteStoragePath) : 0;

    if (fileSize % pageSize != 0) {
      dealWithPageUnAlignedFileSize(absoluteStoragePath, fileSize, regionAllocationLock);
    }

    return new MMappedFileStorage(absoluteStoragePath, pageSize, regionAllocationLock);
  }

  private void dealWithPageUnAlignedFileSize(@NotNull Path storagePath,
                                             long fileSize,
                                             @NotNull RegionAllocationAtomicityLock regionAllocationLock) throws IOException {
    // It is generally an error to have the file size unaligned with page size.
    //    Exception #1: if next-page-expansion wasn't finished because of the app crash -> check for it.
    //    Exception #2: we're explicitly asked to ignore that and just expand the file to page-aligned size
    //    Otherwise: fail

    //Maybe there was a file expansion interrupted by app crash/kill?
    long startOfSuspiciousRegion = (fileSize / pageSize) * pageSize;
    RegionAllocationAtomicityLock.Region region = regionAllocationLock.region(startOfSuspiciousRegion, pageSize);
    if (region.isUnfinished()) {
      //There is an 'unfinished' region -- i.e. file expansion and zeroing started, but was interrupted by app crash/kill.
      // We _could_ do nothing here, and let MMappedFileStorage deal with it, but this leaves the invariant "channel.size is
      // aligned with page size" broken for a while (until next page allocation 'fixes' it), which is undesirable.
      // Better finish the work right now:
      LOG.warn("mmapped file region " + region + " allocation & zeroing has been started, " +
               "but hasn't been properly finished -- IDE was crashed/killed? -> try finishing the job");
      try (FileChannel channel = FileChannel.open(storagePath, WRITE)) {
        IOUtil.fillFileRegionWithZeros(channel, startOfSuspiciousRegion, startOfSuspiciousRegion + pageSize);
      }
      region.finish();
      return;
    }

    switch (ifFileNotPageAligned) {
      case THROW_EXCEPTION -> {
        throw new IOException("[" + storagePath + "]: fileSize(=" + fileSize + " b) is not page(=" + pageSize + " b)-aligned");
      }

      case EXPAND_FILE -> {
        LOG.warn("[" + storagePath + "]: fileSize(=" + fileSize + " b) is not page(=" + pageSize + " b)-aligned -> expand until aligned");
        //expand (zeroes) file up to the next page:
        long fileSizeRoundedUpToPageSize = ((fileSize / pageSize) + 1) * pageSize;
        region.start();
        try (FileChannel channel = FileChannel.open(storagePath, WRITE)) {
          IOUtil.allocateFileRegion(channel, fileSizeRoundedUpToPageSize);
        }
        region.finish();
      }

      case CLEAN -> {
        LOG.warn("[" + storagePath + "]: fileSize(=" + fileSize + " b) is not page(=" + pageSize + " b)-aligned -> delete & re-create");
        NioFiles.deleteRecursively(storagePath);
      }
    }
  }

  private void checkParentDirectories(@NotNull Path storagePath) throws IOException {
    Path parentDir = storagePath.getParent();
    if (Files.exists(parentDir)) {
      return;
    }

    if (createParentDirectoriesIfNotExist) {
      Files.createDirectories(parentDir);
      return;
    }

    throw new NoSuchFileException("Parent directory of [" + storagePath + "] is not exist, and .createDirectoriesIfNotExist=false");
  }

  @Override
  public String toString() {
    return "MMappedFileStorageFactory{" +
           "pageSize: " + pageSize +
           ", ifNotPageAligned: " + ifFileNotPageAligned +
           ", createParentDirectoriesIfNotExist: " + createParentDirectoriesIfNotExist +
           '}';
  }

  public enum IfNotPageAligned {
    /** Expand the file (and fill with zeros) until it is N*pageSize */
    EXPAND_FILE,
    THROW_EXCEPTION,
    /** Clear the file content, and open as-if-fresh-new */
    CLEAN
  }
}
