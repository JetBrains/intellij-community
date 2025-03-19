// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestBase;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;

public class DurableMapOverBlobStorageTest extends DurableMapTestBase<String, String, DurableMapOverBlobStorage<String, String>> {

  public DurableMapOverBlobStorageTest() {
    super(STRING_SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<DurableMapOverBlobStorage<String, String>> factory() {
    var allocationStrategy = new WriterDecidesStrategy(StreamlinedBlobStorageHelper.MAX_CAPACITY, 256);
    StorageFactory<? extends StreamlinedBlobStorage> blobStorageFactory =
      MMappedFileStorageFactory.withDefaults()
        .compose(
          mappedFileStorage -> new StreamlinedBlobStorageOverMMappedFile(mappedFileStorage, allocationStrategy)
        );

    //TODO RC: test both cases: with and without valueEquality
    KeyDescriptorEx<String> stringAsUTF8 = stringAsUTF8();
    return DurableMapOverBlobStorage.Factory.defaults(
      blobStorageFactory,
      stringAsUTF8,
      stringAsUTF8
    );
  }

  @Override
  protected boolean isAppendOnly() {
    return false;
  }


  @Override
  @Disabled("Recovery is not yet implemented for this implementation")
  public void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsLost() throws IOException {
    super.mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsLost();
  }
}
