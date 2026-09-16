// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.openapi.util.Ref;
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverBlocksStore;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer.Entry;
import com.intellij.platform.util.io.storages.intmultimaps.Durable;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.BiPredicate;

import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64.DEFAULT_SEGMENT_SIZE;
import static com.intellij.util.io.IOUtil.KiB;

/// Stores
/// - key-value entries in `DATA` blocks
/// - hash lookup in `LOOKUP` blocks
@ApiStatus.Internal
public final class DurableMapOverBlocks<K, V> implements DurableMap<K, V> {
  public static final int DEFAULT_DATA_BLOCK_CONTENT_LENGTH = 64 * KiB;

  private final @NotNull BlocksStore blocksStore;

  /// here `(key, value)` pairs, serialized by [entryExternalizer], are stored
  private final @NotNull RecordStorageOverBlocks entries;

  //@GuardedBy(lock)
  private final @NotNull IntToMultiLongMap keyHashToRecordRefMap;

  private final @NotNull EqualityPolicy<? super K> keyEquality;
  private final @Nullable EqualityPolicy<? super V> valueEquality;
  private final @NotNull EntryExternalizer<K, V> entryExternalizer;

  private final transient Object lock = new Object();

  private boolean closed;
  private boolean cleaned;

  private DurableMapOverBlocks(@NotNull BlocksStore blocksStore,
                               @NotNull RecordStorageOverBlocks entries,
                               @NotNull IntToMultiLongMap keyHashToRecordRefMap,
                               @NotNull EqualityPolicy<? super K> keyEquality,
                               @Nullable EqualityPolicy<? super V> valueEquality,
                               @NotNull EntryExternalizer<K, V> entryExternalizer) {
    this.blocksStore = blocksStore;
    this.entries = entries;
    this.keyHashToRecordRefMap = keyHashToRecordRefMap;
    this.keyEquality = keyEquality;
    this.valueEquality = valueEquality;
    this.entryExternalizer = entryExternalizer;
  }

  /// Opens the map with a persistent lookup in `LOOKUP` blocks
  public static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore blocksStore,
                                                                int preferredBlockContentLength,
                                                                @NotNull EqualityPolicy<? super K> keyEquality,
                                                                @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    return open(blocksStore, preferredBlockContentLength, keyEquality, null, entryExternalizer);
  }

  /// Opens the map with a persistent lookup in `LOOKUP` blocks
  public static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore blocksStore,
                                                                int preferredBlockContentLength,
                                                                @NotNull EqualityPolicy<? super K> keyEquality,
                                                                @Nullable EqualityPolicy<? super V> valueEquality,
                                                                @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    var lookupStorage = new ExtendibleHashMapStorageOverBlocksStore(
      blocksStore,
      DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP.persistentCode(),
      DEFAULT_SEGMENT_SIZE * 2 // need large indexes!
    );
    var rebuildLookup = lookupStorage.isEmpty();
    ExtendibleHashMapInt32ToInt64 lookup;
    try {
      lookup = new ExtendibleHashMapInt32ToInt64(lookupStorage);
    }
    catch (IOException | RuntimeException | Error failure) {
      try {
        lookupStorage.closeAndClean();
      }
      catch (RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }


    try {
      if (!rebuildLookup && !lookup.wasProperlyClosed()) {
        rebuildLookup = true;
        lookup.clear();
      }
      return open(blocksStore, preferredBlockContentLength, lookup, rebuildLookup, keyEquality, valueEquality, entryExternalizer);
    }
    catch (IOException | RuntimeException | Error failure) {
      try {
        if (rebuildLookup) {
          lookup.closeAndClean();
        }
        else {
          lookup.close();
        }
      }
      catch (IOException | RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /// Opens the map with the specified lookup.
  /// A non-durable lookup must be empty and is rebuilt from the committed DATA records.
  public static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore store,
                                                                int preferredBlockContentLength,
                                                                @NotNull IntToMultiLongMap lookup,
                                                                @NotNull EqualityPolicy<? super K> keyEquality,
                                                                @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    return open(store, preferredBlockContentLength, lookup, keyEquality, null, entryExternalizer);
  }

  /// Opens the map with the specified lookup.
  /// A non-durable lookup must be empty and is rebuilt from the committed DATA records.
  public static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore store,
                                                                int preferredBlockContentLength,
                                                                @NotNull IntToMultiLongMap lookup,
                                                                @NotNull EqualityPolicy<? super K> keyEquality,
                                                                @Nullable EqualityPolicy<? super V> valueEquality,
                                                                @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    return open(store, preferredBlockContentLength, lookup, !(lookup instanceof Durable), keyEquality, valueEquality, entryExternalizer);
  }

  private static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore blocksStore,
                                                                 int preferredBlockContentLength,
                                                                 @NotNull IntToMultiLongMap lookup,
                                                                 boolean rebuildLookup,
                                                                 @NotNull EqualityPolicy<? super K> keyEquality,
                                                                 @Nullable EqualityPolicy<? super V> valueEquality,
                                                                 @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    if (rebuildLookup && !lookup.isEmpty()) {
      throw new IllegalArgumentException("The lookup must be empty before recovery");
    }
    var blockCatalog = DurableMapBlockCatalog.open(blocksStore);
    var mapEntries = RecordStorageOverBlocks.open(blockCatalog, preferredBlockContentLength);
    var durableMapImpl = new DurableMapOverBlocks<>(blocksStore, mapEntries, lookup, keyEquality, valueEquality, entryExternalizer);
    if (rebuildLookup) {
      durableMapImpl.rebuildLookupFromRecords();
    }
    return durableMapImpl;
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    synchronized (lock) {
      ensureOpen();
      return findRecordRef(key, adjustedHash(key)) != NO_VALUE;
    }
  }

  @Override
  public @Nullable V get(@NotNull K key) throws IOException {
    int adjustedHash = adjustedHash(key);
    synchronized (lock) {
      ensureOpen();
      var entry = findEntry(key, adjustedHash);
      return entry == null ? null : entry.value();
    }
  }

  @Override
  public void put(@NotNull K key, @Nullable V value) throws IOException {
    var keyHash = adjustedHash(key);
    synchronized (lock) {
      ensureOpen();
      var oldRecordRef = findRecordRef(key, keyHash);
      if (valueEquality != null && value != null && oldRecordRef != NO_VALUE) {
        var oldValue = readEntry(oldRecordRef).value();
        if (oldValue != null && valueEquality.isEqual(value, oldValue)) {
          return;
        }
      }
      // TODO A crash after DATA commit and before the lookup update leaves a stale lookup marked as properly closed.
      //      Mark the durable lookup as dirty before appendEntry().
      var newRecordRef = appendEntry(key, value);

      if (value == null) {
        if (oldRecordRef != NO_VALUE) {
          keyHashToRecordRefMap.remove(keyHash, oldRecordRef);
        }
      }
      else if (oldRecordRef == NO_VALUE) {
        keyHashToRecordRefMap.put(keyHash, newRecordRef);
      }
      else if (!keyHashToRecordRefMap.replace(keyHash, oldRecordRef, newRecordRef)) {
        throw new AssertionError("The old record reference disappeared from the lookup");
      }
    }
  }

  @Override
  public void remove(@NotNull K key) throws IOException {
    put(key, null);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyHashToRecordRefMap.forEach((_, recordRef) -> {
        var key = readKey(recordRef);
        if (key == null) {
          throw new CorruptedException("The lookup references a deleted record " + recordRef);
        }
        return processor.process(key);
      });
    }
  }

  @Override
  public boolean forEachEntry(@NotNull BiPredicate<? super K, ? super V> processor) throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyHashToRecordRefMap.forEach((_, recordRef) -> {
        var entry = readEntry(recordRef);
        return processor.test(entry.key(), entry.value());
      });
    }
  }

  @Override
  public boolean isEmpty() throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyHashToRecordRefMap.isEmpty();
    }
  }

  @Override
  public int size() throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyHashToRecordRefMap.size();
    }
  }

  @Override
  public boolean isDirty() {
    return blocksStore.isDirty();
  }

  @Override
  public void force() throws IOException {
    synchronized (lock) {
      ensureOpen();
      blocksStore.flush();
      if (keyHashToRecordRefMap instanceof Durable durableLookup) {
        durableLookup.flush();
      }
    }
  }

  @Override
  public boolean isClosed() {
    synchronized (lock) {
      return closed;
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closeLookup();
      closed = true;
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    synchronized (lock) {
      if (!closed) {
        //we don't need closeAndClean(): store.drop() drops all the blocks owned by this storage anyway
        closeLookup();
      }
      if (!cleaned) {
        blocksStore.drop();
        cleaned = true;
      }
      closed = true;
    }
  }

  private void rebuildLookupFromRecords() throws IOException {
    entries.forEachCommittedRecord((recordRef, payload) -> {
      var entry = entryExternalizer.read(payload.asByteBuffer());
      var keyHash = adjustedHash(entry.key());
      var oldRecordRef = findRecordRef(entry.key(), keyHash);
      if (entry.isValueVoid()) {
        if (oldRecordRef != NO_VALUE) {
          keyHashToRecordRefMap.remove(keyHash, oldRecordRef);
        }
      }
      else if (oldRecordRef == NO_VALUE) {
        keyHashToRecordRefMap.put(keyHash, recordRef);
      }
      else if (!keyHashToRecordRefMap.replace(keyHash, oldRecordRef, recordRef)) {
        throw new AssertionError("The old record reference disappeared during lookup recovery");
      }
    });
  }

  private void closeLookup() throws IOException {
    if (keyHashToRecordRefMap instanceof Durable durableLookup) {
      durableLookup.close();
    }
    else {
      keyHashToRecordRefMap.clear();//cut off memory
    }
  }

  private int adjustedHash(@NotNull K key) {
    return HashUtils.adjustHash(keyEquality.getHashCode(key));
  }

  private long findRecordRef(@NotNull K key, int adjustedHash) throws IOException {
    return keyHashToRecordRefMap.lookup(adjustedHash, candidateRef -> {
      var candidateKey = readKey(candidateRef);
      return candidateKey != null && keyEquality.isEqual(key, candidateKey);
    });
  }

  private @Nullable Entry<K, V> findEntry(@NotNull K key, int adjustedHash) throws IOException {
    var result = new Ref<Entry<K, V>>();
    keyHashToRecordRefMap.lookup(adjustedHash, candidateRef -> {
      var entry = entryExternalizer.readIfKeyMatch(entries.read(candidateRef).asByteBuffer(), key);
      if (entry == null) {
        return false;
      }
      result.set(entry);
      return true;
    });
    return result.get();
  }

  private @Nullable K readKey(long recordRef) throws IOException {
    return entryExternalizer.readKey(entries.read(recordRef).asByteBuffer());
  }

  private @NotNull Entry<K, V> readEntry(long recordRef) throws IOException {
    return entryExternalizer.read(entries.read(recordRef).asByteBuffer());
  }

  private long appendEntry(@NotNull K key, @Nullable V value) throws IOException {
    KnownSizeRecordWriter writer = entryExternalizer.writerFor(key, value);
    return entries.append(writer.recordSize(), payload -> writer.write(payload.asByteBuffer()));
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The map is already closed");
    }
  }
}
