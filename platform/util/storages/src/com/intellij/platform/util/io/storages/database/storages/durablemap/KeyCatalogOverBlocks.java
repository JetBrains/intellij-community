// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex;
import com.intellij.util.Processor;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.KEYS;
import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;

/// Stores each serialized key once and maps its hash to a stable key record reference
/// Basically, it is an analog of [com.intellij.util.io.Enumerator] (=unique-zed `key <-> id` catalog), but with
/// additional mutable field (`valueRecordRef`) attached to each key.
///
/// The owning map serializes all operations with its map lock.
@ApiStatus.Internal
public final class KeyCatalogOverBlocks<K> {
  private final @NotNull RecordStorageOverBlocks keys;
  private final @NotNull RecordRefIndex keyHashToRecordRefIndex;
  private final @NotNull KeyDescriptorEx<K> keyDescriptor;

  private KeyCatalogOverBlocks(@NotNull RecordStorageOverBlocks keys,
                               @NotNull RecordRefIndex keyHashToRecordRefIndex,
                               @NotNull KeyDescriptorEx<K> keyDescriptor) {
    this.keys = keys;
    this.keyHashToRecordRefIndex = keyHashToRecordRefIndex;
    this.keyDescriptor = keyDescriptor;
  }

  public static <K> @NotNull KeyCatalogOverBlocks<K> open(@NotNull DurableMapBlockCatalog blockCatalog,
                                                          int preferredBlockContentLength,
                                                          @NotNull RecordRefIndex keyHashToRecordRefIndex,
                                                          @NotNull KeyDescriptorEx<K> keyDescriptor) throws IOException {
    var keys = RecordStorageOverBlocks.open(blockCatalog, KEYS, preferredBlockContentLength);
    return new KeyCatalogOverBlocks<>(keys, keyHashToRecordRefIndex, keyDescriptor);
  }

  /// Finds an existing key record or appends one stable record for a new key.
  public long findOrAppend(@NotNull K key) throws IOException {
    var keyHash = adjustedHash(key);
    var existingRecordRef = findRecordRef(key, keyHash);
    if (existingRecordRef != NO_VALUE) {
      return existingRecordRef;
    }

    var keyWriter = keyDescriptor.writerFor(key);
    var recordSize = KeyRecordLayout.recordSize(keyWriter.recordSize());
    keyHashToRecordRefIndex.markDirty();
    var keyRecordRef = keys.append(recordSize, record -> {
      KeyRecordLayout.initialize(record, keyHash);
      keyWriter.write(record.asSlice(KeyRecordLayout.HEADER_SIZE).asByteBuffer());
    });
    if (!keyHashToRecordRefIndex.put(keyHash, keyRecordRef)) {
      throw new CorruptedException("The key record reference is already in LOOKUP: " + keyRecordRef);
    }
    return keyRecordRef;
  }

  public long findRecordRef(@NotNull K key) throws IOException {
    return findRecordRef(key, adjustedHash(key));
  }

  public @NotNull K readKey(long keyRecordRef) throws IOException {
    return readKey(keys.read(keyRecordRef));
  }

  public long valueRecordRef(long keyRecordRef) throws IOException {
    return KeyRecordLayout.valueRecordRef(keys.read(keyRecordRef));
  }

  public void updateValueRecordRef(long keyRecordRef, long valueRecordRef) throws IOException {
    keys.updatePayload(keyRecordRef, record -> KeyRecordLayout.updateValueRecordRef(record, valueRecordRef));
  }

  /// Processes keys in their append order without reading `LOOKUP` or `VALUES`.
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    return keys.forEachCommittedRecord((_, record) -> processor.process(readKey(record)));
  }

  private long findRecordRef(@NotNull K key, int keyHash) throws IOException {
    return keyHashToRecordRefIndex.lookup(keyHash, candidateRef -> {
      var record = keys.read(candidateRef);
      var storedKeyHash = KeyRecordLayout.keyHash(record);
      if (storedKeyHash != keyHash) {
        throw new CorruptedException(
          "LOOKUP hash " + keyHash + " does not match key record " + candidateRef + " hash " + storedKeyHash
        );
      }
      return keyDescriptor.isEqual(key, readKey(record));
    });
  }

  private @NotNull K readKey(@NotNull MemorySegment record) throws IOException {
    MemorySegment recordPayload = KeyRecordLayout.keyPayload(record);
    ByteBuffer recordPayloadAsBuffer = recordPayload.asByteBuffer();
    return keyDescriptor.read(recordPayloadAsBuffer);
  }

  private int adjustedHash(@NotNull K key) {
    return HashUtils.adjustHash(keyDescriptor.getHashCode(key));
  }
}
