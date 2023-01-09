// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageLockFree;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assume.assumeTrue;

/**
 *
 */
public class AttributesStorageOnTheTopOfStreamlinedBlobStorageLockFreeTest
  extends AttributesStorageOnTheTopOfStreamlinedBlobStorageTestBase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "ageCacheUtils.LOCK_FREE_VFS_ENABLED=false: the FilePageCacheLockFree must be enabled for this test",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(final Path storagePath) throws IOException {
    final PagedFileStorageLockFree pagedStorage = new PagedFileStorageLockFree(
      this.storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true
    );
    storage = new StreamlinedBlobStorageOverLockFreePagesStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    return new AttributesStorageOverBlobStorage(storage);
  }
}
