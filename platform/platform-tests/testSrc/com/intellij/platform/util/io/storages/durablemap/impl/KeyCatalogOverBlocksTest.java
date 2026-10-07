// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.KeyCatalogOverBlocks;
import com.intellij.platform.util.io.storages.database.storages.durablemap.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.InMemoryIntToMultiLongMap;
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.KEYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies stable key records and collision handling in the separate key catalog.
public class KeyCatalogOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int BLOCK_SIZE = 128;

  @Test
  public void repeatedLookupKeepsOneStableKeyRecord(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var index = new TrackingRecordRefIndex();
    long keyRecordRef;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var catalog = KeyCatalogOverBlocks.open(DurableMapBlockCatalog.open(store), BLOCK_SIZE, index, stringAsUTF8());

      keyRecordRef = catalog.findOrAppend("key");
      catalog.updateValueRecordRef(keyRecordRef, 41);

      assertEquals(keyRecordRef, catalog.findOrAppend("key"));
      assertEquals(keyRecordRef, catalog.findRecordRef("key"));
      assertEquals(41, catalog.valueRecordRef(keyRecordRef));
      assertEquals(1, index.size());
      assertEquals(1, index.dirtyMarkCount);
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var catalog = KeyCatalogOverBlocks.open(DurableMapBlockCatalog.open(store), BLOCK_SIZE, index, stringAsUTF8());
      assertEquals(keyRecordRef, catalog.findOrAppend("key"));
      assertEquals(41, catalog.valueRecordRef(keyRecordRef));

      var recordRefs = new ArrayList<Long>();
      var records = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), KEYS, BLOCK_SIZE);
      records.forEachCommittedRecord((recordRef, _) -> {
        recordRefs.add(recordRef);
        return true;
      });
      assertEquals(List.of(keyRecordRef), recordRefs);
    }
  }

  @Test
  public void hashCollisionsKeepIndependentKeyRecords(@TempDir Path databaseDirectory) throws Exception {
    assertEquals("FB".hashCode(), "Ea".hashCode(), "The test keys must collide");
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var index = new TrackingRecordRefIndex();
      var catalog = KeyCatalogOverBlocks.open(
        DurableMapBlockCatalog.open(database.openStore("map", 1)), BLOCK_SIZE, index, stringAsUTF8()
      );

      var firstRef = catalog.findOrAppend("FB");
      var secondRef = catalog.findOrAppend("Ea");

      assertNotEquals(firstRef, secondRef);
      assertEquals(firstRef, catalog.findRecordRef("FB"));
      assertEquals(secondRef, catalog.findRecordRef("Ea"));
      assertEquals("FB", catalog.readKey(firstRef));
      assertEquals("Ea", catalog.readKey(secondRef));
      assertEquals(2, index.size());
    }
  }

  @Test
  public void processKeysReadsOnlyTheSequentialCatalog(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var index = new TrackingRecordRefIndex();
      var catalog = KeyCatalogOverBlocks.open(
        DurableMapBlockCatalog.open(database.openStore("map", 1)), BLOCK_SIZE, index, stringAsUTF8()
      );
      catalog.findOrAppend("first");
      var keyWithValueRef = catalog.findOrAppend("removed");
      catalog.updateValueRecordRef(keyWithValueRef, 73);
      catalog.findOrAppend("last");

      index.rejectReads = true;
      var keys = new ArrayList<String>();
      assertTrue(catalog.processKeys(keys::add));

      assertEquals(List.of("first", "removed", "last"), keys, "The catalog must keep all keys regardless of value state");

      var keysBeforeStop = new ArrayList<String>();
      var allKeysProcessed = catalog.processKeys(key -> {
        keysBeforeStop.add(key);
        return !key.equals("removed");
      });
      assertFalse(allKeysProcessed);
      assertEquals(List.of("first", "removed"), keysBeforeStop);
    }
  }

  private static final class TrackingRecordRefIndex implements RecordRefIndex {
    private final InMemoryIntToMultiLongMap delegate = new InMemoryIntToMultiLongMap();
    private int dirtyMarkCount;
    private boolean rejectReads;

    @Override
    public boolean put(int key, long value) {
      return delegate.put(key, value);
    }

    @Override
    public boolean replace(int key, long oldValue, long newValue) {
      return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public long lookup(int key, @NotNull ValueAcceptor valueAcceptor) throws IOException {
      if (rejectReads) {
        throw new AssertionError("Sequential key processing must not read LOOKUP");
      }
      return delegate.lookup(key, valueAcceptor);
    }

    @Override
    public boolean remove(int key, long value) {
      return delegate.remove(key, value);
    }

    @Override
    public boolean forEach(@NotNull KeyValueProcessor processor) throws IOException {
      if (rejectReads) {
        throw new AssertionError("Sequential key processing must not read LOOKUP");
      }
      return delegate.forEach(processor);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public void markDirty() {
      dirtyMarkCount++;
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }

    @Override
    public void closeKeepingDirty() { }
  }
}
