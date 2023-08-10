// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class StreamlinedBlobStorageOverLockFreePagesStorageTest
  extends StreamlinedBlobStorageTestBase<StreamlinedBlobStorageOverLockFreePagesStorage> {

  @Test
  public void newStorage_HasVersion_OfCurrentStorageFormat() throws Exception {
    assertEquals(
      "New storage version == STORAGE_VERSION_CURRENT",
      storage.getStorageVersion(),
      StreamlinedBlobStorageOverLockFreePagesStorage.STORAGE_VERSION_CURRENT
    );
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "PageCacheUtils.LOCK_FREE_VFS_ENABLED must be true for this test to run",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  public StreamlinedBlobStorageOverLockFreePagesStorageTest(final @NotNull Integer pageSize,
                                                            final @NotNull SpaceAllocationStrategy strategy) {
    super(pageSize, strategy);
  }

  @Override
  protected int maxPayloadSize(int pageSize) {
    return Math.min(StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY, pageSize) - 10;
  }


  @Override
  protected StreamlinedBlobStorageOverLockFreePagesStorage openStorage(final Path pathToStorage) throws IOException {
    PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(
        pathToStorage,
        LOCK_CONTEXT,
        pageSize,
        PageContentLockingStrategy.LOCK_PER_PAGE
      );
    return new StreamlinedBlobStorageOverLockFreePagesStorage(
      pagedStorage,
      allocationStrategy
    );
  }

  @Override
  public void tearDown() throws Exception {
    if (storage != null) {
      System.out.printf("Storage after test: %d records allocated, %d deleted, %d relocated, live records %.1f%% of total \n",
                        storage.recordsAllocated(),
                        storage.recordsDeleted(),
                        storage.recordsRelocated(),
                        storage.liveRecordsCount() * 100.0 / storage.recordsAllocated()
      );
      System.out.printf("                    %d bytes live payload, %d live capacity, live payload %.1f%% of total storage size \n",
                        storage.totalLiveRecordsPayloadBytes(),
                        storage.totalLiveRecordsCapacityBytes(),
                        storage.totalLiveRecordsPayloadBytes() * 100.0 / storage.sizeInBytes() //including _storage_ header
      );
    }
    super.tearDown();
  }
}