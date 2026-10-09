// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.KeyRecordLayout;
import com.intellij.platform.util.io.storages.database.storages.durablemap.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.KEYS;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.VALUES;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.PATCH;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.SNAPSHOT;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.TOMBSTONE;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the binary payload formats of `KEYS` and `VALUES` records.
public class DurableMapRecordLayoutsTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int BLOCK_SIZE = 128;

  @Test
  public void fixedLayoutsKeepTheirPersistentOffsets() {
    assertEquals(12, KeyRecordLayout.HEADER_SIZE);
    assertEquals(0, KeyRecordLayout.LAYOUT.byteOffset(groupElement("valueRecordRef")));
    assertEquals(8, KeyRecordLayout.LAYOUT.byteOffset(groupElement("keyHash")));

    assertEquals(9, ValueRecordLayout.HEADER_SIZE);
    assertEquals(0, ValueRecordLayout.LAYOUT.byteOffset(groupElement("reference")));
    assertEquals(8, ValueRecordLayout.LAYOUT.byteOffset(groupElement("kind")));
  }

  @Test
  public void keyRecordKeepsTheStableKeyAndMutableValueReference() throws Exception {
    var keyBytes = new byte[]{1, 2, 3};
    var recordSize = KeyRecordLayout.recordSize(keyBytes.length);
    try (var arena = Arena.ofConfined()) {
      var storage = arena.allocate(recordSize + Integer.BYTES, Long.BYTES);
      var record = storage.asSlice(Integer.BYTES, recordSize);

      KeyRecordLayout.write(record, 42, MemorySegment.ofArray(keyBytes));

      assertEquals(KeyRecordLayout.NO_VALUE_RECORD_REF, KeyRecordLayout.valueRecordRef(record));
      assertEquals(42, KeyRecordLayout.keyHash(record));
      assertArrayEquals(keyBytes, KeyRecordLayout.keyPayload(record).toArray(JAVA_BYTE));
      assertTrue(KeyRecordLayout.keyPayload(record).isReadOnly());

      KeyRecordLayout.updateValueRecordRef(record, 17);

      assertEquals(17, KeyRecordLayout.valueRecordRef(record));
      assertEquals(42, KeyRecordLayout.keyHash(record), "Updating the value head must preserve the key hash");
      assertArrayEquals(keyBytes, KeyRecordLayout.keyPayload(record).toArray(JAVA_BYTE));
    }
  }

  @Test
  public void keyAndValueRecordsUseTheirOwnBlockRoles(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var keyBytes = new byte[]{10, 20};
    var snapshotBytes = new byte[]{30, 40};
    var patchBytes = new byte[]{50};
    long keyRecordRef;
    long snapshotRef;
    long patchRef;
    long tombstoneRef;
    try (var database = factory.open(databaseDirectory)) {
      var catalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var keys = RecordStorageOverBlocks.open(catalog, KEYS, /*appendOnly: */ false, BLOCK_SIZE);
      var values = RecordStorageOverBlocks.open(catalog, VALUES, /*appendOnly: */ true, BLOCK_SIZE);

      keyRecordRef = keys.append(KeyRecordLayout.recordSize(keyBytes.length), record ->
        KeyRecordLayout.write(record, 73, MemorySegment.ofArray(keyBytes))
      );
      snapshotRef = values.append(ValueRecordLayout.recordSize(snapshotBytes.length), record ->
        ValueRecordLayout.writeSnapshot(record, keyRecordRef, MemorySegment.ofArray(snapshotBytes))
      );
      patchRef = values.append(ValueRecordLayout.recordSize(patchBytes.length), record ->
        ValueRecordLayout.writePatch(record, snapshotRef, MemorySegment.ofArray(patchBytes))
      );
      tombstoneRef = values.append(ValueRecordLayout.recordSize(0), record ->
        ValueRecordLayout.writeTombstone(record, keyRecordRef)
      );

      assertEquals(1, catalog.blocks(KEYS).size());
      assertEquals(1, catalog.blocks(VALUES).size());
      assertTrue(catalog.blocks(ENTRIES).isEmpty(), "The separate format must not allocate ENTRIES blocks");
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var catalog = DurableMapBlockCatalog.open(store);
      var keys = RecordStorageOverBlocks.open(catalog, KEYS, /*appendOnly: */ false, BLOCK_SIZE);
      var values = RecordStorageOverBlocks.open(catalog, VALUES, /*appendOnly: */ true, BLOCK_SIZE);

      assertEquals(73, KeyRecordLayout.keyHash(keys.read(keyRecordRef)));
      assertArrayEquals(keyBytes, KeyRecordLayout.keyPayload(keys.read(keyRecordRef)).toArray(JAVA_BYTE));
      assertValueRecord(values.read(snapshotRef), SNAPSHOT, keyRecordRef, snapshotBytes);
      assertValueRecord(values.read(patchRef), PATCH, snapshotRef, patchBytes);
      assertValueRecord(values.read(tombstoneRef), TOMBSTONE, keyRecordRef, new byte[0]);
    }
  }

  @Test
  public void readersRejectInvalidRecordPayloads() {
    assertThrows(
      CorruptedException.class,
      () -> KeyRecordLayout.keyHash(MemorySegment.ofArray(new byte[KeyRecordLayout.HEADER_SIZE - 1]))
    );
    assertThrows(
      CorruptedException.class,
      () -> ValueRecordLayout.read(MemorySegment.ofArray(new byte[ValueRecordLayout.HEADER_SIZE - 1]))
    );

    var unknownKind = MemorySegment.ofArray(new byte[ValueRecordLayout.HEADER_SIZE]);
    ValueRecordLayout.KIND_HANDLE.set(unknownKind, 0L, (byte)99);
    assertThrows(CorruptedException.class, () -> ValueRecordLayout.read(unknownKind));

    var missingReference = MemorySegment.ofArray(new byte[ValueRecordLayout.HEADER_SIZE]);
    ValueRecordLayout.KIND_HANDLE.set(missingReference, 0L, SNAPSHOT.persistentCode());
    assertThrows(CorruptedException.class, () -> ValueRecordLayout.read(missingReference));

    var tombstoneWithPayload = MemorySegment.ofArray(new byte[ValueRecordLayout.HEADER_SIZE + 1]);
    ValueRecordLayout.KIND_HANDLE.set(tombstoneWithPayload, 0L, TOMBSTONE.persistentCode());
    ValueRecordLayout.REFERENCE_HANDLE.set(tombstoneWithPayload, 0L, 1L);
    assertThrows(CorruptedException.class, () -> ValueRecordLayout.read(tombstoneWithPayload));
  }

  private static void assertValueRecord(@NotNull MemorySegment recordPayload,
                                        @NotNull ValueRecordLayout.Kind expectedKind,
                                        long expectedReference,
                                        byte @NotNull [] expectedPayload) throws CorruptedException {
    var record = ValueRecordLayout.read(recordPayload);
    assertEquals(expectedKind, record.kind());
    assertEquals(expectedReference, record.reference());
    assertArrayEquals(expectedPayload, record.payload().toArray(JAVA_BYTE));
    assertTrue(record.payload().isReadOnly());
  }
}
