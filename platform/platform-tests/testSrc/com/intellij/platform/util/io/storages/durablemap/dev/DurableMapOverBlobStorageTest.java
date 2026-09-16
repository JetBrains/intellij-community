// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.KeyValueStoreScenarios.StringKeyScenarios;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestBase;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.BasicScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.CompactionScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.LookupCorruptionRecoveryScenarios;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import org.jetbrains.annotations.NotNull;
import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.KeyValueTestData.STRING_SUBSTRATE_DECODER;

@SuppressWarnings("JUnitTestCaseWithNoTests")
public class DurableMapOverBlobStorageTest
  extends DurableMapTestBase<String, String, DurableMapOverBlobStorage<String, String>>
  implements BasicScenarios<String, String, DurableMapOverBlobStorage<String, String>>,
             StringKeyScenarios<String, DurableMapOverBlobStorage<String, String>>,
             CompactionScenarios<String, String, DurableMapOverBlobStorage<String, String>>,
             LookupCorruptionRecoveryScenarios<String, String, DurableMapOverBlobStorage<String, String>> {

  public DurableMapOverBlobStorageTest() {
    super(STRING_SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<DurableMapOverBlobStorage<String, String>> factory() {
    var allocationStrategy = new WriterDecidesStrategy(StreamlinedBlobStorageOverMMappedFile.MAX_CAPACITY, 256);
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
}
