// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PagedFileStorageLockFree;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.NON_EXISTENT_ATTR_RECORD_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SmallStreamlinedBlobStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 *
 */
@RunWith(Theories.class)
public class StreamlinedBlobStorageOverLockFreePagedStorageTest
  extends StreamlinedBlobStorageTestBase<StreamlinedBlobStorageOverLockFreePagesStorage> {
  public StreamlinedBlobStorageOverLockFreePagedStorageTest(final @NotNull Integer pageSize,
                                                            final @NotNull SpaceAllocationStrategy strategy) {
    super(pageSize, strategy);
  }


  @Override
  protected StreamlinedBlobStorageOverLockFreePagesStorage openStorage(final Path pathToStorage) throws IOException {
    final PagedFileStorageLockFree pagedStorage = new PagedFileStorageLockFree(
      pathToStorage,
      LOCK_CONTEXT,
      pageSize,
      true
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