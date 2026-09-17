// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.KeyValueStoreScenarios.StringKeyScenarios;
import com.intellij.platform.util.io.storages.KeyValueTestData;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.BasicScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.LookupLossRecoveryScenarios;
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
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;

/// Runs the shared durable map scenarios while one database stays open for the full test
@SuppressWarnings("JUnitTestCaseWithNoTests")
public class DurableMapOverBlocksScenariosTest
  implements DurableMapTestContext<String, String, DurableMapOverBlocks<String, String>>,
             BasicScenarios<String, String, DurableMapOverBlocks<String, String>>,
             StringKeyScenarios<String, DurableMapOverBlocks<String, String>>,
             LookupLossRecoveryScenarios<String, String, DurableMapOverBlocks<String, String>> {

  //TODO RC: Add a test for the crash window between the DATA commit and the LOOKUP update after the dirty protocol is implemented.

  private static final int DATABASE_CHUNK_SIZE = 1024 * 1024;
  private static final int MAP_DATA_VERSION = 1;
  private static final String MAIN_STORE_NAME = "map";
  private static final IntFunction<Map.Entry<String, String>> KEY_VALUE_DECODER = KeyValueTestData.STRING_SUBSTRATE_DECODER;

  private BlocksDatabase database;
  private BlocksStore mainStore;
  private DurableMapOverBlocks<String, String> storage;
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
  public @NotNull DurableMapOverBlocks<String, String> storageUnderTest() {
    return storage;
  }

  @Override
  public @NotNull IntFunction<? extends Map.Entry<String, String>> keyValueSubstrateDecoder() {
    return KEY_VALUE_DECODER;
  }

  @Override
  public @NotNull DurableMapOverBlocks<String, String> reopenStorage() throws IOException {
    storage.close();
    storage = openMap(mainStore);
    return storage;
  }

  @Override
  public boolean isAppendOnlyStorage() {
    return true;
  }

  @Override
  public @NotNull DurableMapOverBlocks<String, String> openStorage(@NotNull Path path) throws IOException {
    var storeName = path.getFileName() + "-" + auxiliaryStoreIndex++;
    return openMap(database.openStore(storeName, MAP_DATA_VERSION));
  }

  @Override
  public void closeStorage(@NotNull DurableMapOverBlocks<String, String> storage) throws IOException {
    storage.close();
  }

  @Override
  public void reopenDroppingDurableLookupPart() throws IOException {
    storage.close();
    for (var block : mainStore.blocks()) {
      if (block.role() == DurableMapBlockRole.LOOKUP.persistentCode() && block.state() != RETIRED) {
        if (block.state() == ACTIVE) {
          block.seal();
        }
        block.retire();
      }
    }
    storage = openMap(mainStore);
  }

  @Override
  public void reopenAfterImproperClose() {
    //TODO RC: Implement this hook and add LookupCorruptionRecoveryScenarios without closing the database.
    throw new UnsupportedOperationException("The database fixture cannot emulate an improper close");
  }

  private static @NotNull DurableMapOverBlocks<String, String> openMap(@NotNull BlocksStore store) throws IOException {
    KeyDescriptorEx<String> descriptor = stringAsUTF8();
    var entryExternalizer = new DefaultEntryExternalizer<>(descriptor, descriptor);
    return DurableMapOverBlocks.open(
      store,
      DurableMapOverBlocks.DEFAULT_DATA_BLOCK_CONTENT_LENGTH,
      descriptor,
      descriptor,
      entryExternalizer
    );
  }
}
