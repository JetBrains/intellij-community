// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks;
import com.intellij.platform.util.io.storages.database.storages.durablemap.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer;
import com.intellij.platform.util.io.storages.intmultimaps.Durable;
import com.intellij.platform.util.io.storages.intmultimaps.InMemoryIntToMultiLongMap;
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the DATA log and the disposable hash index of a database map. */
public class DurableMapOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int DATA_BLOCK_CONTENT_LENGTH = 128;

  @Test
  public void putUpdateAndRemoveUseDataRecords(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory);
         var map = createMap(database.openStore("map", 1))) {
      assertTrue(map.isEmpty());

      map.put("key", "first");
      assertEquals("first", map.get("key"));
      assertTrue(map.containsMapping("key"));

      map.put("key", "second");
      assertEquals("second", map.get("key"));
      assertEquals(1, map.size());

      map.remove("key");
      assertNull(map.get("key"));
      assertFalse(map.containsMapping("key"));

      map.put("other", "value");
      map.put("other", null);
      assertFalse(map.containsMapping("other"));
      assertTrue(map.isEmpty());
    }
  }

  @Test
  public void sameValueDoesNotAddAnotherDataRecord(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      try (var map = createMap(store)) {
        map.put("key", "value");
        map.put("key", "value");
      }

      var recordRefs = new ArrayList<Long>();
      var records = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), DATA_BLOCK_CONTENT_LENGTH);
      records.forEachCommittedRecord((recordRef, _) -> recordRefs.add(recordRef));
      assertEquals(1, recordRefs.size());
    }
  }

  @Test
  public void hashCollisionsKeepIndependentRecordReferences(@TempDir Path databaseDirectory) throws Exception {
    assertEquals("FB".hashCode(), "Ea".hashCode(), "The test keys must collide");
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory);
         var map = createMap(database.openStore("map", 1))) {
      map.put("FB", "first");
      map.put("Ea", "second");

      assertEquals("first", map.get("FB"));
      assertEquals("second", map.get("Ea"));

      map.remove("FB");
      assertNull(map.get("FB"));
      assertEquals("second", map.get("Ea"));
    }
  }

  @Test
  public void reopeningRebuildsTheLookupFromDataRecords(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory);
         var map = createMap(database.openStore("map", 1))) {
      map.put("updated", "old");
      map.put("updated", "new");
      map.put("removed", "value");
      map.remove("removed");
      map.put("retained", "value");
    }

    try (var database = factory.open(databaseDirectory);
         var map = createMap(requireMapStore(database))) {
      assertEquals("new", map.get("updated"));
      assertNull(map.get("removed"));
      assertEquals("value", map.get("retained"));
      assertEquals(2, map.size());
    }
  }

  @Test
  public void reopeningIgnoresAnIncompleteFinalRecord(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      try (var map = createMap(store)) {
        map.put("stable", "value");
      }

      var records = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), DATA_BLOCK_CONTENT_LENGTH);
      assertThrows(IOException.class, () -> records.append(16, _ -> {
        throw new IOException("simulated write failure");
      }));
    }

    try (var database = factory.open(databaseDirectory);
         var map = createMap(requireMapStore(database))) {
      assertEquals("value", map.get("stable"));
      map.put("next", "record");
      assertEquals("record", map.get("next"));
    }
  }

  @Test
  public void durableLookupKeepsItsContentAndReceivesLifecycleCalls(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var lookup = new TrackingDurableLookup();
      var map = createMap(store, lookup);
      map.put("key", "value");

      map.force();
      assertEquals(1, lookup.flushCount);

      map.close();
      assertEquals(1, lookup.closeCount);
      assertEquals(1, lookup.size(), "Closing the durable lookup must preserve its content");

      lookup.mutationCount = 0;
      try (var reopenedMap = createMap(store, lookup)) {
        assertEquals("value", reopenedMap.get("key"));
        assertEquals(0, lookup.mutationCount, "Opening the durable lookup must not rebuild it from DATA records");
      }
    }
  }

  private static @NotNull DurableMapOverBlocks<String, String> createMap(@NotNull BlocksStore store) throws IOException {
    return createMap(store, new InMemoryIntToMultiLongMap());
  }

  private static @NotNull DurableMapOverBlocks<String, String> createMap(@NotNull BlocksStore store,
                                                                         @NotNull IntToMultiLongMap lookup) throws IOException {
    KeyDescriptorEx<String> descriptor = stringAsUTF8();
    var entryExternalizer = new DefaultEntryExternalizer<>(descriptor, descriptor);
    return DurableMapOverBlocks.open(store, DATA_BLOCK_CONTENT_LENGTH, lookup, descriptor, descriptor, entryExternalizer);
  }

  private static @NotNull BlocksStore requireMapStore(@NotNull BlocksDatabase database) {
    var store = database.findStore("map");
    if (store == null) {
      throw new AssertionError("Missing store map");
    }
    return store;
  }

  private static final class TrackingDurableLookup implements IntToMultiLongMap, Durable {
    private final InMemoryIntToMultiLongMap delegate = new InMemoryIntToMultiLongMap();
    private int mutationCount;
    private int flushCount;
    private int closeCount;

    @Override
    public boolean put(int key, long value) {
      mutationCount++;
      return delegate.put(key, value);
    }

    @Override
    public boolean replace(int key, long oldValue, long newValue) {
      mutationCount++;
      return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public long lookup(int key, @NotNull ValueAcceptor valueAcceptor) throws IOException {
      return delegate.lookup(key, valueAcceptor);
    }

    @Override
    public boolean remove(int key, long value) {
      mutationCount++;
      return delegate.remove(key, value);
    }

    @Override
    public boolean forEach(@NotNull KeyValueProcessor processor) throws IOException {
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
      mutationCount++;
      delegate.clear();
    }

    @Override
    public void flush() {
      flushCount++;
    }

    @Override
    public void close() {
      closeCount++;
    }
  }
}
