// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeSizeStreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.PagedFileStorage;

import java.io.IOException;
import java.nio.file.Path;

/**
 */
public class AttributesStorageOnTheTopOfLargeStreamlinedBlobStorageTest extends AttributesStorageOnTheTopOfBlobStorageTestBase {
  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(final Path storagePath) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    storage = new LargeSizeStreamlinedBlobStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    return new AttributesStorageOverBlobStorage(storage);
  }

}
