// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.openapi.util.Ref;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.appendonlylog.AppendOnlyLogOverBlock;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverBlocksStore;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer.Entry;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.platform.util.io.storages.intmultimaps.Durable;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.function.BiPredicate;

import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64.DEFAULT_SEGMENT_SIZE;
import static com.intellij.util.io.IOUtil.KiB;

/// Stores
/// - key-value entries in `DATA` blocks
/// - hash lookup in `LOOKUP` blocks
@ApiStatus.Internal
public class DurableMapOverBlocks<K, V> implements DurableMap<K, V> {
  public static final int DEFAULT_DATA_BLOCK_CONTENT_LENGTH = 64 * KiB;

  /// 32K is not enough for indexes
  public static final int SEGMENT_SIZE = DEFAULT_SEGMENT_SIZE * 2;

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
    return open(blocksStore, preferredBlockContentLength, keyEquality, valueEquality, entryExternalizer, null);
  }

  /// Opens a map whose codec can combine snapshots and patches.
  /// Both codecs must use the same value format.
  public static <K, V, P> @NotNull PatchableDurableMap<K, V, P> openPatchable(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull EqualityPolicy<? super K> keyEquality,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull EntryExternalizer<K, V> entryExternalizer,
    @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> patchExternalizer
  ) throws IOException {
    return asPatchable(open(blocksStore, preferredBlockContentLength, keyEquality, valueEquality, entryExternalizer, patchExternalizer));
  }

  private static <K, V> @NotNull DurableMapOverBlocks<K, V> open(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull EqualityPolicy<? super K> keyEquality,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull EntryExternalizer<K, V> entryExternalizer,
    @Nullable PatchableDurableMap.PatchableValueExternalizer<V, ?> patchExternalizer
  ) throws IOException {
    var lookupStorage = new ExtendibleHashMapStorageOverBlocksStore(
      blocksStore,
      DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP.persistentCode(),
      SEGMENT_SIZE
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
      return open(blocksStore, preferredBlockContentLength, lookup, rebuildLookup, keyEquality, valueEquality,
                  entryExternalizer, patchExternalizer);
    }
    catch (IOException | RuntimeException | Error failure) {
      try {
        lookup.closeKeepingDirty();
        if (rebuildLookup) {
          lookupStorage.closeAndClean();
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
  public static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore blocksStore,
                                                                int preferredBlockContentLength,
                                                                @NotNull IntToMultiLongMap lookup,
                                                                @NotNull EqualityPolicy<? super K> keyEquality,
                                                                @Nullable EqualityPolicy<? super V> valueEquality,
                                                                @NotNull EntryExternalizer<K, V> entryExternalizer) throws IOException {
    return open(
      blocksStore,
      preferredBlockContentLength,
      lookup, /*rebuildLookupFromZero: */ !(lookup instanceof Durable),
      keyEquality, valueEquality, entryExternalizer, /*patchExternalizer: */ null
    );
  }

  /// Opens a patchable map with an externally managed lookup.
  /// Both codecs must use the same value format.
  public static <K, V, P> @NotNull PatchableDurableMap<K, V, P> openPatchable(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull IntToMultiLongMap lookup,
    @NotNull EqualityPolicy<? super K> keyEquality,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull EntryExternalizer<K, V> entryExternalizer,
    @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> patchExternalizer
  ) throws IOException {
    return asPatchable(
      open(
        blocksStore,
        preferredBlockContentLength,
        lookup, /*rebuildLookupFromZero: */ !(lookup instanceof Durable),
        keyEquality, valueEquality, entryExternalizer, patchExternalizer
      )
    );
  }

  private static <K, V> @NotNull DurableMapOverBlocks<K, V> open(@NotNull BlocksStore blocksStore,
                                                                 int preferredBlockContentLength,
                                                                 @NotNull IntToMultiLongMap lookup,
                                                                 boolean rebuildLookup,
                                                                 @NotNull EqualityPolicy<? super K> keyEquality,
                                                                 @Nullable EqualityPolicy<? super V> valueEquality,
                                                                 @NotNull EntryExternalizer<K, V> entryExternalizer,
                                                                 @Nullable PatchableDurableMap.PatchableValueExternalizer<V, ?> patchExternalizer) throws IOException {
    if (rebuildLookup && !lookup.isEmpty()) {
      throw new IllegalArgumentException("The lookup must be empty before recovery");
    }
    var blockCatalog = DurableMapBlockCatalog.open(blocksStore);
    var mapEntries = RecordStorageOverBlocks.open(blockCatalog, preferredBlockContentLength);
    DurableMapOverBlocks<K, V> durableMapImpl = (patchExternalizer == null) ?
                                                new DurableMapOverBlocks<>(blocksStore, mapEntries, lookup, keyEquality,
                                                                           valueEquality, entryExternalizer) :
                                                new PatchableMap<>(blocksStore, mapEntries, lookup, keyEquality,
                                                                     valueEquality, entryExternalizer, patchExternalizer);
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
      if (value == null && oldRecordRef == NO_VALUE) {//skip put(key,null) if value is already null
        return;
      }
      var writer = entryExternalizer.writerFor(key, value);
      markLookupDirty();
      try {
        var newRecordRef = entries.append(writer.recordSize(), payload -> writer.write(payload.asByteBuffer()));
        if (value == null) {
          if (!keyHashToRecordRefMap.remove(keyHash, oldRecordRef)) {
            throw new CorruptedException("The old record reference disappeared from the lookup");
          }
        }
        else {
          publishHead(keyHash, oldRecordRef, newRecordRef);
        }
      }
      catch (IOException | RuntimeException | Error failure) {
        closeAfterFailure(failure);
        throw failure;
      }
    }
  }

  /// Serializes patches under the same lock as all other map operations
  protected final <P> void patchValue(@NotNull K key,
                                      @NotNull P patch,
                                      @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> externalizer) throws IOException {
    var keyHash = adjustedHash(key);
    var writer = externalizer.writerForPatch(patch);
    synchronized (lock) {
      ensureOpen();
      var oldHead = findRecordRef(key, keyHash);
      if (oldHead == NO_VALUE) {
        var headerWriter = entryExternalizer.writerForEntryHeader(key);
        int headerSize = headerWriter.recordSize();
        int payloadSize = Math.addExact(headerSize, writer.recordSize());
        markLookupDirty();
        try {
          var newHead = entries.append(payloadSize, payload -> {
            headerWriter.write(payload.asSlice(0, headerSize).asByteBuffer());
            writer.write(payload.asSlice(headerSize).asByteBuffer());
          });
          publishHead(keyHash, NO_VALUE, newHead);
        }
        catch (IOException | RuntimeException | Error failure) {
          closeAfterFailure(failure);
          throw failure;
        }
      }
      else {
        var head = entries.readRecord(oldHead);
        long baseRef = head.previousRef() == NO_VALUE ? oldHead : baseEntryRef(oldHead, head);
        int payloadSize = Math.addExact(Long.BYTES, writer.recordSize());
        markLookupDirty();
        try {
          var newHead = entries.append(payloadSize, oldHead, payload -> {
            payload.set(ValueLayout.JAVA_LONG_UNALIGNED, 0, baseRef);
            writer.write(payload.asSlice(Long.BYTES).asByteBuffer());
          });
          publishHead(keyHash, oldHead, newHead);
        }
        catch (IOException | RuntimeException | Error failure) {
          closeAfterFailure(failure);
          throw failure;
        }
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
      blocksStore.flush();
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
    markLookupDirty();
    Long2ObjectMap<HeadState> heads = new Long2ObjectOpenHashMap<>();
    entries.forEachCommittedRecordWithLinks((recordRef, record) -> {
      if (record.previousRef() != NO_VALUE) {
        long baseRef = baseEntryRef(recordRef, record);
        var previous = heads.remove(record.previousRef());
        if (previous == null || previous.baseEntryRef() != baseRef) {
          throw new CorruptedException("The patch does not continue the current chain: " + recordRef);
        }
        publishHead(previous.hash(), record.previousRef(), recordRef);
        heads.put(recordRef, previous);
        return;
      }

      var key = entryExternalizer.readKey(record.payload().asByteBuffer());
      boolean deleted = key == null;
      if (deleted) {
        key = entryExternalizer.read(record.payload().asByteBuffer()).key();
      }
      int keyHash = adjustedHash(key);
      long oldRecordRef = findRecordRef(key, keyHash);
      heads.remove(oldRecordRef);
      if (deleted) {
        if (oldRecordRef != NO_VALUE) {
          keyHashToRecordRefMap.remove(keyHash, oldRecordRef);
        }
      }
      else {
        publishHead(keyHash, oldRecordRef, recordRef);
        heads.put(recordRef, new HeadState(keyHash, recordRef));
      }
    });
  }

  private record HeadState(int hash, long baseEntryRef) { }

  ///if lookup table implementation supports dirty-flag -- mark lookup table dirty;
  ///Beware: lookup table should be marked 'dirty' (=out-of-sync) as soon as an update is stored to entries -- because
  /// as the update is stored in entries, lookup table becomes (potentially) out-of-sync until flush-ed.
  private void markLookupDirty() throws IOException {
    if (keyHashToRecordRefMap instanceof ExtendibleHashMapInt32ToInt64 lookup) {
      lookup.markDirty();
    }
  }

  private void publishHead(int keyHash, long oldHead, long newHead) throws IOException {
    boolean updated = oldHead == NO_VALUE
                      ? keyHashToRecordRefMap.put(keyHash, newHead)
                      : keyHashToRecordRefMap.replace(keyHash, oldHead, newHead);
    if (!updated) {
      throw new CorruptedException("The record reference disappeared from the lookup: " + oldHead);
    }
  }

  /// A failed write must keep the lookup dirty, even when the caller closes the map
  private void closeAfterFailure(@NotNull Throwable failure) {
    closed = true;
    try {
      if (keyHashToRecordRefMap instanceof ExtendibleHashMapInt32ToInt64 lookup) {
        lookup.closeKeepingDirty();
      }
      else {
        closeLookup();
      }
    }
    catch (IOException | RuntimeException | Error closeFailure) {
      failure.addSuppressed(closeFailure);
    }
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
      var record = entries.readRecord(candidateRef);
      Entry<K, V> entry;
      if (record.previousRef() == NO_VALUE) {
        entry = entryExternalizer.readIfKeyMatch(record.payload().asByteBuffer(), key);
      }
      else {
        var candidateKey = readKey(candidateRef);
        if (candidateKey == null || !keyEquality.isEqual(key, candidateKey)) {
          return false;
        }
        entry = readEntry(candidateRef);
      }
      if (entry == null) {
        return false;
      }
      result.set(entry);
      return true;
    });
    return result.get();
  }

  private @Nullable K readKey(long recordRef) throws IOException {
    var record = entries.readRecord(recordRef);
    if (record.previousRef() != NO_VALUE) {
      record = entries.readRecord(baseEntryRef(recordRef, record));
      if (record.previousRef() != NO_VALUE) {
        throw new CorruptedException("The base entry is a patch: " + recordRef);
      }
    }
    return entryExternalizer.readKey(record.payload().asByteBuffer());
  }

  private @NotNull Entry<K, V> readEntry(long recordRef) throws IOException {
    var record = entries.readRecord(recordRef);
    if (record.previousRef() == NO_VALUE) {
      return entryExternalizer.read(record.payload().asByteBuffer());
    }

    long baseRef = baseEntryRef(recordRef, record);
    var patches = new ArrayList<MemorySegment>();
    long totalSize = 0;
    long currentRef = recordRef;
    while (currentRef != baseRef) {
      if (record.previousRef() == NO_VALUE || baseEntryRef(currentRef, record) != baseRef) {
        throw new CorruptedException("The patch chain has a different base entry: " + currentRef);
      }
      var patch = record.payload().asSlice(Long.BYTES);
      patches.add(patch);
      totalSize += patch.byteSize();
      currentRef = record.previousRef();
      record = entries.readRecord(currentRef);
    }
    if (record.previousRef() != NO_VALUE || entryExternalizer.readKey(record.payload().asByteBuffer()) == null) {
      throw new CorruptedException("The patch chain must start with a non-null entry: " + baseRef);
    }
    totalSize += record.payload().byteSize();
    if (totalSize > Integer.MAX_VALUE) {
      throw new IOException("The patched value exceeds the maximum buffer size: " + totalSize);
    }
    var buffer = ByteBuffer.allocate((int)totalSize);
    buffer.put(record.payload().asByteBuffer());
    for (int i = patches.size() - 1; i >= 0; i--) {
      buffer.put(patches.get(i).asByteBuffer());
    }
    buffer.flip();
    return entryExternalizer.read(buffer);
  }

  /// @return reference to 'base' record, i.e., first record in a chain of 'patches'
  private static long baseEntryRef(long recordRef, @NotNull AppendOnlyLogOverBlock.Record record) throws CorruptedException {
    if (record.payload().byteSize() < Long.BYTES || record.previousRef() <= NO_VALUE || record.previousRef() >= recordRef) {
      throw new CorruptedException("Invalid patch predecessor: " + recordRef);
    }
    long baseRef = record.payload().get(ValueLayout.JAVA_LONG_UNALIGNED, 0);
    if (baseRef <= NO_VALUE || baseRef > record.previousRef()) {
      throw new CorruptedException("Invalid patch base entry: " + recordRef);
    }
    return baseRef;
  }

  @SuppressWarnings("unchecked")
  private static <K, V, P> @NotNull PatchableDurableMap<K, V, P> asPatchable(@NotNull DurableMapOverBlocks<K, V> map) {
    return (PatchableDurableMap<K, V, P>)map;
  }

  ///Opens the 'patching' capability to the shared storage implementation: [patchValue] method is already implemented
  /// in [DurableMapOverBlocks] superclass, but hidden
  private static final class PatchableMap<K, V, P> extends DurableMapOverBlocks<K, V> implements PatchableDurableMap<K, V, P> {
    private final @NotNull PatchableValueExternalizer<V, P> patchExternalizer;

    private PatchableMap(@NotNull BlocksStore blocksStore,
                         @NotNull RecordStorageOverBlocks entries,
                         @NotNull IntToMultiLongMap lookup,
                         @NotNull EqualityPolicy<? super K> keyEquality,
                         @Nullable EqualityPolicy<? super V> valueEquality,
                         @NotNull EntryExternalizer<K, V> entryExternalizer,
                         @NotNull PatchableValueExternalizer<V, P> patchExternalizer) {
      super(blocksStore, entries, lookup, keyEquality, valueEquality, entryExternalizer);
      this.patchExternalizer = patchExternalizer;
    }

    @Override
    public void patchValue(@NotNull K key, @NotNull P patch) throws IOException {
      //the patching is already implemented, but hidden
      super.patchValue(key, patch, patchExternalizer);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The map is already closed");
    }
  }
}
