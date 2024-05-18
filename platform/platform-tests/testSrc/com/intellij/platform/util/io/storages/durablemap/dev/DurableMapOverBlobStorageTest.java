// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestBase;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.durablemap.DurableMapFactory.MAP_FILE_SUFFIX;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST;

public class DurableMapOverBlobStorageTest extends DurableMapTestBase<String, String, DurableMapOverBlobStorage<String, String>> {

  public DurableMapOverBlobStorageTest() {
    super(STRING_SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<DurableMapOverBlobStorage<String, String>> factory() {
    ExtendibleMapFactory mapFactory = ExtendibleMapFactory
      .mediumSize()
      .cleanIfFileIncompatible()
      .ifNotClosedProperly(IGNORE_AND_HOPE_FOR_THE_BEST);

    MMappedFileStorageFactory mmappedStorageFactory = MMappedFileStorageFactory.withDefaults();
    var allocationStrategy = new WriterDecidesStrategy(StreamlinedBlobStorageHelper.MAX_CAPACITY, 256);
    StorageFactory<StreamlinedBlobStorageOverMMappedFile> blobStorageFactory = mmappedStorageFactory.compose(
      mappedFileStorage -> new StreamlinedBlobStorageOverMMappedFile(mappedFileStorage, allocationStrategy)
    );

    //TODO RC: test both cases: with and without valueEquality
    KeyDescriptorEx<String> stringAsUTF8 = stringAsUTF8();
    return new StorageFactory<>() {
      @Override
      public @NotNull DurableMapOverBlobStorage<String, String> open(@NotNull Path storagePath) throws IOException {
        String name = storagePath.getFileName().toString();
        Path mapPath = storagePath.resolveSibling(name + MAP_FILE_SUFFIX);
        return mapFactory.wrapStorageSafely(
          mapPath,
          keyHashToIdsMap -> {
            return blobStorageFactory.wrapStorageSafely(
              storagePath,
              keyValuesStorage -> {
                return new DurableMapOverBlobStorage<>(
                  keyValuesStorage,
                  keyHashToIdsMap,
                  stringAsUTF8,
                  stringAsUTF8,
                  new DefaultEntryExternalizer<>(stringAsUTF8, stringAsUTF8)
                );
              });
          }
        );
      }
    };
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
