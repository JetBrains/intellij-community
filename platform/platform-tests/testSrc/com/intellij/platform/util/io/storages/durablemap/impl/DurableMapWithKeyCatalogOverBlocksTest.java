// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapWithKeyCatalogOverBlocks;
import com.intellij.platform.util.io.storages.database.storages.durablemap.KeyRecordLayout;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.KEYS;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.VALUES;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION;
import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64.DEFAULT_SEGMENT_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the map API over separate key and value records.
public class DurableMapWithKeyCatalogOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int SMALL_RECORD_BLOCK_SIZE = 128;

  @Test
  public void mapOperationsKeepStableKeysAndSeparateValues(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var map = openStringMap(database);
      assertTrue(map.isEmpty());

      map.put("updated", "first");
      map.put("updated", "second");
      map.put("removed", "value");
      map.remove("removed");
      map.put("FB", "collision-one");
      map.put("Ea", "collision-two");

      assertEquals("second", map.get("updated"));
      assertNull(map.get("removed"));
      assertFalse(map.containsMapping("removed"));
      assertEquals("collision-one", map.get("FB"));
      assertEquals("collision-two", map.get("Ea"));
      assertEquals(3, map.size());

      var keys = new ArrayList<String>();
      assertTrue(map.processKeys(keys::add));
      assertEquals(List.of("updated", "removed", "FB", "Ea"), keys, "Key processing must include the removed key");

      var entries = new LinkedHashMap<String, String>();
      var allEntriesProcessed = map.forEachEntry((key, value) -> {
        entries.put(key, value);
        return true;
      });
      assertTrue(allEntriesProcessed);
      assertEquals(
        new LinkedHashMap<>(java.util.Map.of("updated", "second", "FB", "collision-one", "Ea", "collision-two")),
        entries
      );
    }

    try (var database = factory.open(databaseDirectory)) {
      var map = openStringMap(database);
      assertEquals("second", map.get("updated"));
      assertNull(map.get("removed"));
      assertEquals("collision-one", map.get("FB"));
      assertEquals("collision-two", map.get("Ea"));
      assertEquals(3, map.size());
    }
  }

  @Test
  public void updateAfterReopenCanModifyAKeyFromAnOldBlock(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      try (var map = openStringMap(store, SMALL_RECORD_BLOCK_SIZE)) {
        for (var keyIndex = 0; keyIndex < 16; keyIndex++) {
          map.put("key-" + keyIndex, "initial");
        }
      }

      var blockCatalog = DurableMapBlockCatalog.open(store);
      assertTrue(blockCatalog.blocks(KEYS).size() > 1, "The test must allocate more than one key block");
    }

    try (var database = factory.open(databaseDirectory);
         var map = openStringMap(requireMapStore(database), SMALL_RECORD_BLOCK_SIZE)) {
      map.put("key-0", "updated");

      assertEquals("updated", map.get("key-0"));
    }
  }

  @Test
  public void patchChainsStartFromValuesAndFromAnEmptyValue(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    var codec = new AppendStringCodec();
    try (var database = factory.open(databaseDirectory)) {
      var map = openPatchableStringMap(database, codec);

      map.patchValue("key", "a");
      map.patchValue("key", "b");
      assertEquals("ab", map.get("key"));

      map.put("key", "reset");
      map.patchValue("key", "!");
      assertEquals("reset!", map.get("key"));

      map.remove("key");
      map.patchValue("key", "new");
      assertEquals("new", map.get("key"));
      assertEquals(1, map.size());
    }

    try (var database = factory.open(databaseDirectory)) {
      assertEquals("new", openPatchableStringMap(database, codec).get("key"));
    }
  }

  @Test
  public void dirtyOpenReplaysSnapshotsPatchesAndTombstones(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var database = factory.open(databaseDirectory);
    var map = openPatchableStringMap(database.openStore("map", 1), new AppendStringCodec());
    map.put("patched", "base");
    map.patchValue("patched", "+patch");
    map.put("removed", "value");
    map.remove("removed");
    map.put("restored", "old");
    map.remove("restored");
    map.put("restored", "new");

    database.close(); // Leave the map lookup dirty to simulate a process failure.

    try (var reopenedDatabase = factory.open(databaseDirectory);
         var recoveredMap = openPatchableStringMap(requireMapStore(reopenedDatabase), new AppendStringCodec())) {
      assertEquals("base+patch", recoveredMap.get("patched"));
      assertNull(recoveredMap.get("removed"));
      assertEquals("new", recoveredMap.get("restored"));
      assertEquals(2, recoveredMap.size());
    }
  }

  @Test
  public void dirtyReplayPublishesCommittedSnapshotWithoutKeyHeadUpdate(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      try (var map = openStringMap(store)) {
        map.put("key", "old");
      }

      var blockCatalog = DurableMapBlockCatalog.open(store);
      var keyRecordRef = onlyKeyRecordRef(blockCatalog);
      var lookup = openLookup(blockCatalog);
      lookup.markDirty();
      var values = RecordStorageOverBlocks.open(
        blockCatalog.appendableRecordBlocks(VALUES),
        /*appendOnly: */ true,
        DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH
      );
      var payload = serializedString("new");
      values.append(ValueRecordLayout.recordSize(Math.toIntExact(payload.byteSize())), record ->
        ValueRecordLayout.writeSnapshot(record, keyRecordRef, payload)
      );
      lookup.closeKeepingDirty();
    }

    try (var database = factory.open(databaseDirectory);
         var map = openStringMap(requireMapStore(database))) {
      assertEquals("new", map.get("key"), "Recovery must publish a committed value record that was not stored in the key head");
    }
  }

  @Test
  public void dirtyReplayRejectsPatchForAnOldHead(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      try (var map = openStringMap(store)) {
        map.put("key", "base");
      }

      var blockCatalog = DurableMapBlockCatalog.open(store);
      var keys = RecordStorageOverBlocks.open(
        blockCatalog.appendableRecordBlocks(KEYS),
        /*appendOnly: */ false,
        DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH
      );
      var keyRecordRef = onlyKeyRecordRef(blockCatalog);
      var oldHead = KeyRecordLayout.valueRecordRef(keys.read(keyRecordRef));
      var lookup = openLookup(blockCatalog);
      lookup.markDirty();
      var values = RecordStorageOverBlocks.open(
        blockCatalog.appendableRecordBlocks(VALUES),
        /*appendOnly: */ true,
        DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH
      );
      var firstPatch = serializedString("first");
      values.append(ValueRecordLayout.recordSize(Math.toIntExact(firstPatch.byteSize())), record ->
        ValueRecordLayout.writePatch(record, oldHead, firstPatch)
      );
      var stalePatch = serializedString("stale");
      values.append(ValueRecordLayout.recordSize(Math.toIntExact(stalePatch.byteSize())), record ->
        ValueRecordLayout.writePatch(record, oldHead, stalePatch)
      );
      lookup.closeKeepingDirty();
    }

    try (var database = factory.open(databaseDirectory)) {
      var failure = assertThrows(CorruptedException.class, () -> openStringMap(requireMapStore(database)));
      assertTrue(failure.getMessage().contains("does not continue the current value chain"));
    }
  }

  @Test
  public void dirtyReplayKeepsKeyWhoseFirstValueWriteFailed(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var database = factory.open(databaseDirectory);
    var store = database.openStore("map", 1);
    var descriptor = stringAsUTF8();
    var failingExternalizer = new DataExternalizerEx<String>() {
      @Override
      public String read(@NotNull MemorySegment input) throws IOException {
        return descriptor.read(input);
      }

      @Override
      public @NotNull KnownSizeRecordWriter writerFor(@NotNull String value) {
        return new KnownSizeRecordWriter() {
          @Override
          public void write(@NotNull MemorySegment target, long startingOffset) throws IOException {
            throw new IOException("simulated value write failure");
          }

          @Override
          public int recordSize() {
            return 1;
          }
        };
      }
    };
    var map = DurableMapWithKeyCatalogOverBlocks.open(
      store, DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH, descriptor, null, failingExternalizer
    );
    assertThrows(IOException.class, () -> map.put("key", "value"));
    assertTrue(map.isClosed(), "A failed value publication must close the map");
    database.close();

    try (var reopenedDatabase = factory.open(databaseDirectory);
         var recoveredMap = openStringMap(requireMapStore(reopenedDatabase))) {
      assertNull(recoveredMap.get("key"));
      var keys = new ArrayList<String>();
      assertTrue(recoveredMap.processKeys(keys::add));
      assertEquals(List.of("key"), keys, "Recovery must preserve the committed key record");

      recoveredMap.put("key", "recovered");
      keys.clear();
      assertTrue(recoveredMap.processKeys(keys::add));
      assertEquals(List.of("key"), keys, "The next put must reuse the recovered key record");
    }
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database) throws IOException {
    var descriptor = stringAsUTF8();
    return database.openMap("map", 1, KEY_VALUE_SEPARATELY, descriptor, descriptor);
  }

  private static @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> openStringMap(@NotNull BlocksStore store) throws IOException {
    return openStringMap(store, DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH);
  }

  private static @NotNull DurableMapWithKeyCatalogOverBlocks<String, String> openStringMap(@NotNull BlocksStore store,
                                                                                            int recordBlockContentLength)
    throws IOException {
    var descriptor = stringAsUTF8();
    return DurableMapWithKeyCatalogOverBlocks.open(
      store, recordBlockContentLength, descriptor, descriptor, descriptor
    );
  }

  private static @NotNull PatchableDurableMap<String, String, String> openPatchableStringMap(
    @NotNull DurableDatabase database,
    @NotNull AppendStringCodec codec
  ) throws IOException {
    return database.openMap("map", 1, KEY_VALUE_SEPARATELY, stringAsUTF8(), codec);
  }

  private static @NotNull PatchableDurableMap<String, String, String> openPatchableStringMap(
    @NotNull BlocksStore store,
    @NotNull AppendStringCodec codec
  ) throws IOException {
    return DurableMapWithKeyCatalogOverBlocks.openPatchable(
      store, DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH, stringAsUTF8(), null, codec
    );
  }

  private static @NotNull BlocksStore requireMapStore(@NotNull BlocksDatabase database) {
    var store = database.findStore("map");
    if (store == null) {
      throw new AssertionError("Missing store map");
    }
    return store;
  }

  private static long onlyKeyRecordRef(@NotNull DurableMapBlockCatalog blockCatalog) throws IOException {
    var keys = RecordStorageOverBlocks.open(
      blockCatalog.appendableRecordBlocks(KEYS),
      /*appendOnly: */ false,
      DurableMapWithKeyCatalogOverBlocks.DEFAULT_RECORD_BLOCK_CONTENT_LENGTH
    );
    var keyRecordRef = new long[]{NO_VALUE};
    keys.forEachCommittedRecord((recordRef, _) -> {
      if (keyRecordRef[0] != NO_VALUE) {
        throw new AssertionError("Expected one key record");
      }
      keyRecordRef[0] = recordRef;
      return true;
    });
    if (keyRecordRef[0] == NO_VALUE) {
      throw new AssertionError("Missing key record");
    }
    return keyRecordRef[0];
  }

  private static @NotNull ExtendibleHashMapInt32ToInt64 openLookup(@NotNull DurableMapBlockCatalog blockCatalog) throws IOException {
    var storage = new ExtendibleHashMapStorageOverLookupBlocks(
      blockCatalog.lookupBlocks(IMPLEMENTATION_ID, INITIAL_GENERATION),
      ExtendibleHashMapInt32ToInt64.REQUIRED_HEADER_SIZE,
      DEFAULT_SEGMENT_SIZE * 2
    );
    return new ExtendibleHashMapInt32ToInt64(storage);
  }

  private static @NotNull MemorySegment serializedString(@NotNull String value) throws IOException {
    var writer = stringAsUTF8().writerFor(value);
    var bytes = new byte[writer.recordSize()];
    writer.write(MemorySegment.ofArray(bytes), 0);
    return MemorySegment.ofArray(bytes);
  }

  private static final class AppendStringCodec implements PatchableDurableMap.PatchableValueExternalizer<String, String> {
    private static final byte SET = 1;
    private static final byte APPEND = 2;

    @Override
    public String read(@NotNull MemorySegment input) throws IOException {
      var result = new StringBuilder();
      long offset = 0;
      while (offset < input.byteSize()) {
        var command = input.get(ValueLayout.JAVA_BYTE, offset++);
        if (input.byteSize() - offset < Integer.BYTES) {
          throw new IOException("Missing string command length");
        }
        var length = input.get(JAVA_INT_UNALIGNED, offset);
        offset += Integer.BYTES;
        if (length < 0 || length > input.byteSize() - offset) {
          throw new IOException("Invalid string command length " + length);
        }
        var bytes = input.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE);
        offset += length;
        var text = new String(bytes, StandardCharsets.UTF_8);
        switch (command) {
          case SET -> {
            result.setLength(0);
            result.append(text);
          }
          case APPEND -> result.append(text);
          default -> throw new IOException("Unknown string command " + command);
        }
      }
      return result.toString();
    }

    @Override
    public @NotNull DataExternalizerEx.KnownSizeRecordWriter writerFor(@NotNull String value) {
      return writer(SET, value);
    }

    @Override
    public @NotNull DataExternalizerEx.KnownSizeRecordWriter writerForPatch(@NotNull String patch) {
      return writer(APPEND, patch);
    }

    private static @NotNull DataExternalizerEx.KnownSizeRecordWriter writer(byte command, @NotNull String text) {
      var textBytes = text.getBytes(StandardCharsets.UTF_8);
      var bytes = new byte[Byte.BYTES + Integer.BYTES + textBytes.length];
      var record = MemorySegment.ofArray(bytes);
      record.set(ValueLayout.JAVA_BYTE, 0, command);
      record.set(JAVA_INT_UNALIGNED, Byte.BYTES, textBytes.length);
      MemorySegment.copy(textBytes, 0, record, ValueLayout.JAVA_BYTE, Byte.BYTES + Integer.BYTES, textBytes.length);
      return DataExternalizerEx.fromBytes(bytes);
    }
  }
}
