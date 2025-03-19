// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;


@RunWith(Theories.class)
public class StreamlinedBlobStorageOverMMappedFileTest extends StreamlinedBlobStorageTestBase<StreamlinedBlobStorageOverMMappedFile> {

  public StreamlinedBlobStorageOverMMappedFileTest(@NotNull Integer pageSize,
                                                   @NotNull SpaceAllocationStrategy strategy) {
    super(pageSize, strategy);
  }

  @Override
  protected StreamlinedBlobStorageOverMMappedFile openStorage(@NotNull Path pathToStorage) throws IOException {
    //RC: mapped storage is currently badly suited for small pageSize -- too many pages mapped, too big internal
    //    pages array. So I just make all pageSize >= 1Mb. This hack could be removed as soon as MMappedFileStorage.pages
    //    become re-sizeable
    int pageSize = Math.max(this.pageSize, 1 << 20);
    return MMappedFileStorageFactory.withDefaults()
      .pageSize(pageSize)
      .wrapStorageSafely(
        pathToStorage,
        storage -> new StreamlinedBlobStorageOverMMappedFile(storage, allocationStrategy)
      );
  }

  @Override
  public void tearDown() throws Exception {
    if (storage != null && !storage.isClosed()) {
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

  @Test
  public void newStorage_HasVersion_OfCurrentStorageFormat() throws Exception {
    assertEquals(
      "New storage version == STORAGE_VERSION_CURRENT",
      StreamlinedBlobStorageOverMMappedFile.STORAGE_VERSION_CURRENT,
      storage.getStorageVersion()
    );
  }
}