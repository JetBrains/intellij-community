// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class StreamlinedBlobStorageOverLockFreePagedStorageTest
  extends StreamlinedBlobStorageTestBase<StreamlinedBlobStorageOverLockFreePagedStorage> {

  @Test
  public void newStorage_HasVersion_OfCurrentStorageFormat() throws Exception {
    assertEquals(
      "New storage version == STORAGE_VERSION_CURRENT",
      StreamlinedBlobStorageOverLockFreePagedStorage.STORAGE_VERSION_CURRENT,
      storage.getStorageVersion()
    );
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED must be true for this test to run",
      PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED
    );
  }

  public StreamlinedBlobStorageOverLockFreePagedStorageTest(final @NotNull Integer pageSize,
                                                            final @NotNull SpaceAllocationStrategy strategy) {
    super(pageSize, strategy);
  }

  @Override
  protected StreamlinedBlobStorageOverLockFreePagedStorage openStorage(final Path pathToStorage) throws IOException {
    return IOUtil.wrapSafely(
      new PagedFileStorageWithRWLockedPageContent(pathToStorage, LOCK_CONTEXT, pageSize, PageContentLockingStrategy.LOCK_PER_PAGE),
      pagedStorage -> new StreamlinedBlobStorageOverLockFreePagedStorage(
        pagedStorage,
        allocationStrategy
      )
    );
  }

  @Override
  public void tearDown() throws Exception {
    if (storage != null && !storage.isClosed() ) {
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