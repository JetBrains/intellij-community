// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl.heavy.attributes;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory.IfNotPageAligned.EXPAND_FILE;

/**
 *
 */
public class AttributesStorageOnTheTopOfStreamlinedBlobStorageOverMMappedTest extends AttributesStorageOnTheTopOfBlobStorageTestBase {
  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(Path storagePath) throws IOException {
    return new AttributesStorageOverBlobStorage(
      MMappedFileStorageFactory.withDefaults()
        .pageSize(PAGE_SIZE)
        //mmapped and !mmapped storages have the same binary layout, so mmapped storage could inherit all the
        // data from non-mmapped -- the only 'migration' needed is to page-align the file:
        .ifFileIsNotPageAligned(EXPAND_FILE)
        .wrapStorageSafely(
          storagePath,
          storage -> new StreamlinedBlobStorageOverMMappedFile(
            storage,
            new DataLengthPlusFixedPercentStrategy(64, 256, StreamlinedBlobStorageHelper.MAX_CAPACITY, 30)
          )
        )
    );
  }
}
