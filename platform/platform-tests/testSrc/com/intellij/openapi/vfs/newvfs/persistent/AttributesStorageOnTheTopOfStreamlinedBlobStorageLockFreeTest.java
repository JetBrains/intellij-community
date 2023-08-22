// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage.AttributeEntry;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage.AttributesRecord;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase.AttributeRecord.newAttributeRecord;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class AttributesStorageOnTheTopOfStreamlinedBlobStorageLockFreeTest
  extends AttributesStorageOnTheTopOfBlobStorageTestBase {

  @BeforeClass
  public static void checkLockFreePageCacheIsEnabled() throws Exception {
    assumeTrue(
      "PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED=false: the FilePageCacheLockFree must be enabled for this test",
      PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED
    );
  }

  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(final Path storagePath) throws IOException {
    final PagedFileStorageWithRWLockedPageContent pagedStorage =
      new PagedFileStorageWithRWLockedPageContent(
        this.storagePath,
        LOCK_CONTEXT,
        PAGE_SIZE,
        PageContentLockingStrategy.LOCK_PER_PAGE
      );
    storage = new StreamlinedBlobStorageOverLockFreePagesStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    return new AttributesStorageOverBlobStorage(storage);
  }

  @Test
  public void recordWithZeroPayload_CouldBeInserted_AndReadBackAsIs() throws IOException {
    final AttributeRecord recordWithZeroPayload = attributes.insertOrUpdateRecord(
      newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
        .withAttributeBytes(new byte[0], 0),
      attributesStorage
    );

    assertTrue(
      "Inserted record with 0 payload is exist",
      recordWithZeroPayload.existsInStorage(attributesStorage)
    );
    final byte[] readBack = recordWithZeroPayload.readValueFromStorage(attributesStorage);
    assertEquals(
      "Payload is still 0",
      0,
      readBack.length
    );
  }
}
