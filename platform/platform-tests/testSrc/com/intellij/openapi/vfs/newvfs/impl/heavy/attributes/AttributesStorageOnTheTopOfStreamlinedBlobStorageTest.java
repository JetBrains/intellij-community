// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl.heavy.attributes;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverPagedStorage;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.PagedFileStorage;

import java.io.IOException;
import java.nio.file.Path;

/**
 */
public class AttributesStorageOnTheTopOfStreamlinedBlobStorageTest extends AttributesStorageOnTheTopOfBlobStorageTestBase {
  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(Path storagePath) throws IOException {
    PagedFileStorage pagedStorage = new PagedFileStorage(
      storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    storage = new StreamlinedBlobStorageOverPagedStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(64, 256, StreamlinedBlobStorageHelper.MAX_CAPACITY, 30)
    );
    return new AttributesStorageOverBlobStorage(storage);
  }

}
