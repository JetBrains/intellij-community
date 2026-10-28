// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.InlineKeyDescriptorEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.storages.BlockSizingPolicy;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex;
import com.intellij.util.Processor;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static com.intellij.platform.util.io.storages.database.DurableDatabaseFactory.ENABLE_SELF_CHECKS;
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
    return open(blockCatalog, new BlockSizingPolicy.FixedSize(preferredBlockContentLength), keyHashToRecordRefIndex, keyDescriptor);
  }

  public static <K> @NotNull KeyCatalogOverBlocks<K> open(@NotNull DurableMapBlockCatalog blockCatalog,
                                                          @NotNull BlockSizingPolicy blockSizePolicy,
                                                          @NotNull RecordRefIndex keyHashToRecordRefIndex,
                                                          @NotNull KeyDescriptorEx<K> keyDescriptor) throws IOException {
    var keys = RecordStorageOverBlocks.open(
      blockCatalog.appendableRecordBlocks(KEYS),
      /*appendOnly: */ false,
      blockSizePolicy
    );
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
      keyWriter.write(record, KeyRecordLayout.HEADER_SIZE);
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

  /// Rebuilds `LOOKUP` from stable key records and resets value heads before value replay.
  void rebuildLookupAndResetValueRecordRefs() throws IOException {
    keyHashToRecordRefIndex.clear();
    keys.forEachCommittedRecord((keyRecordRef, readOnlyRecord) -> {
      var keyHash = KeyRecordLayout.keyHash(readOnlyRecord);
      //TODO RC: its quite ineffective to request the same record again just to update it -- better have a write-allowed
      //         keys.forEachCommittedRecord() variant
      keys.updatePayload(keyRecordRef, writableRecord -> KeyRecordLayout.updateValueRecordRef(writableRecord, NO_VALUE));
      if (!keyHashToRecordRefIndex.put(keyHash, keyRecordRef)) {
        // (keyHash, keyRecordRef) pair should be unique!
        throw new CorruptedException("The key-record reference is already in LOOKUP: " + keyRecordRef);
      }
      return true;
    });
  }

  /// Verifies that the reference identifies a committed key record
  /// @throws CorruptedException if keyRecordRef does NOT refer a committed key record
  void requireKeyRecord(long keyRecordRef) throws IOException {
    //FIXME RC: read key just to check keyRecordRef seems like an overkill!
    try {
      KeyRecordLayout.keyHash(keys.read(keyRecordRef));
    }
    catch (IllegalArgumentException e) {
      throw new CorruptedException("Unknown key record reference " + keyRecordRef, e);
    }
  }

  /// Processes keys in their append order without reading `LOOKUP` or `VALUES`.
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    return keys.forEachCommittedRecord((_, record) -> processor.process(readKey(record)));
  }

  /// Uses the lookup for inline keys and the sequential catalog for other keys.
  /// The lookup order can differ from the append order.
  boolean processKeysUsingLookupIfInline(@NotNull Processor<? super K> processor) throws IOException {
    if (keyDescriptor instanceof InlineKeyDescriptorEx<K> inlineDescriptor) {
      return keyHashToRecordRefIndex.forEach((keyHash, recordRef) -> {
        var key = HashUtils.isAdjustedHashAmbiguous(keyHash) ?
                  readKey(recordRef) : //hash conflict: read actual key to disambiguate
                  inlineDescriptor.fromInt(keyHash);
        return processor.process(key);
      });
    }
    return processKeys(processor);
  }

  /// Processes key records in append order and includes their current value references.
  /// @return `true` if all records were processed, or `false` if the processor stopped the traversal.
  public boolean processKeyRecords(@NotNull KeyRecordProcessor<? super K> processor) throws IOException {
    return keys.forEachCommittedRecord(
      (recordRef, record) -> {
        K key = readKey(record);
        long valueRecordRef = KeyRecordLayout.valueRecordRef(record);
        return processor.process(recordRef, key, valueRecordRef);
      }
    );
  }

  /// Processes key record references and their current value heads without deserializing keys.
  boolean processValueRecordRefs(@NotNull ValueRecordRefProcessor processor) throws IOException {
    return keys.forEachCommittedRecord(
      (recordRef, record) -> {
        var valueRecordRef = KeyRecordLayout.valueRecordRef(record);
        return processor.process(recordRef, valueRecordRef);
      }
    );
  }

  private long findRecordRef(@NotNull K key, int keyHash) throws IOException {
    return keyHashToRecordRefIndex.lookup(keyHash, candidateRef -> {
      var record = keys.read(candidateRef);
      if (ENABLE_SELF_CHECKS) {
        var storedKeyHash = KeyRecordLayout.keyHash(record);
        if (storedKeyHash != keyHash) {
          throw new CorruptedException("LOOKUP hash " + keyHash + " does not match key record " + candidateRef + " hash " + storedKeyHash);
        }
      }

      return keyDescriptor.isEqual(key, readKey(record));
    });
  }

  private @NotNull K readKey(@NotNull MemorySegment record) throws IOException {
    return keyDescriptor.read(KeyRecordLayout.keyPayload(record));
  }

  private int adjustedHash(@NotNull K key) {
    return HashUtils.adjustHash(keyDescriptor.getHashCode(key));
  }

  @FunctionalInterface
  public interface KeyRecordProcessor<K> {
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(long keyRecordRef, @NotNull K key, long valueRecordRef) throws IOException;
  }

  @FunctionalInterface
  interface ValueRecordRefProcessor {
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(long keyRecordRef, long valueRecordRef) throws IOException;
  }
}
