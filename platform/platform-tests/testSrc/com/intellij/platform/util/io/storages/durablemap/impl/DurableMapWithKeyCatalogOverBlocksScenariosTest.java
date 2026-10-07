// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.KeyValueStoreScenarios.StringKeyScenarios;
import com.intellij.platform.util.io.storages.KeyValueTestData;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapWithKeyCatalogOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.BasicScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.IntFunction;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;

/// Runs the shared durable map scenarios for separate key and value records.
@SuppressWarnings("JUnitTestCaseWithNoTests")
public class DurableMapWithKeyCatalogOverBlocksScenariosTest
  implements DurableMapTestContext<String, String, DurableMapWithKeyCatalogOverBlocks<String, String>>,
             BasicScenarios<String, String, DurableMapWithKeyCatalogOverBlocks<String, String>>,
             StringKeyScenarios<String, DurableMapWithKeyCatalogOverBlocks<String, String>> {

  private static final int DATABASE_CHUNK_SIZE = 1024 * 1024;
  private static final int MAP_DATA_VERSION = 1;
  private static final String MAIN_STORE_NAME = "map";
  private static final IntFunction<Map.Entry<String, String>> KEY_VALUE_DECODER = KeyValueTestData.STRING_SUBSTRATE_DECODER;

  private BlocksDatabase database;
  private BlocksStore mainStore;
  private DurableMapWithKeyCatalogOverBlocks<String, String> storage;
  private int auxiliaryStoreIndex;

  @BeforeEach
  void setUp(@TempDir Path databaseDirectory) throws IOException {
    database = new BlocksDatabaseFactory(DATABASE_CHUNK_SIZE).open(databaseDirectory);
    mainStore = database.openStore(MAIN_STORE_NAME, MAP_DATA_VERSION);
    storage = openMap(mainStore);
    storage.force();
  }

  @AfterEach
  void tearDown() throws IOException {
    var openedDatabase = database;
    if (openedDatabase == null) {
      if (storage != null) {
        storage.close();
      }
      return;
    }

    try (openedDatabase) {
      if (storage != null) {
        storage.close();
      }
    }
  }

  @Override
  public @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> storageUnderTest() {
    return storage;
  }

  @Override
  public @NotNull IntFunction<? extends Map.Entry<String, String>> keyValueSubstrateDecoder() {
    return KEY_VALUE_DECODER;
  }

  @Override
  public @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> reopenStorage() throws IOException {
    storage.close();
    storage = openMap(mainStore);
    return storage;
  }

  @Override
  public boolean isAppendOnlyStorage() {
    return true;
  }

  @Override
  public boolean processKeysMayContainRemovedKeys() {
    return true;
  }

  @Override
  public @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> openStorage(@NotNull Path path) throws IOException {
    var storeName = path.getFileName() + "-" + auxiliaryStoreIndex++;
    return openMap(database.openStore(storeName, MAP_DATA_VERSION));
  }

  @Override
  public void closeStorage(@NotNull DurableMapWithKeyCatalogOverBlocks<String, String> storage) throws IOException {
    storage.close();
  }

  @Override
  public void reopenDroppingDurableLookupPart() {
    throw new UnsupportedOperationException("Lookup recovery belongs to the next implementation stage");
  }

  @Override
  public void reopenAfterImproperClose() {
    throw new UnsupportedOperationException("Dirty recovery belongs to the next implementation stage");
  }

  private static @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> openMap(@NotNull BlocksStore store) throws IOException {
    var descriptor = stringAsUTF8();
    return DurableMapWithKeyCatalogOverBlocks.open(
      store,
      DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH,
      descriptor,
      descriptor,
      descriptor
    );
  }
}
