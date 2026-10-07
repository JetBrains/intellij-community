// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.openapi.util.Ref;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.function.BiPredicate;

import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.VALUES;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.PATCH;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.SNAPSHOT;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.ValueRecordLayout.Kind.TOMBSTONE;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID;
import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64.DEFAULT_SEGMENT_SIZE;
import static com.intellij.util.io.IOUtil.KiB;

/// Stores stable keys in `KEYS` blocks and value histories in `VALUES` blocks.
@ApiStatus.Internal
public class DurableMapWithKeyCatalogOverBlocks<K, V> implements DurableMap<K, V> {
  static final int IMPLEMENTATION_VERSION = 1;

  public static final int DEFAULT_RECORD_BLOCK_CONTENT_LENGTH = 64 * KiB;
  //TODO RC: segmentSize should be configurable on map creation, and should be a part of map (store) metadata
  private static final int SEGMENT_SIZE = DEFAULT_SEGMENT_SIZE * 2;

  private final @NotNull BlocksStore blocksStore;

  private final @NotNull RecordRefIndex keyHashToRecordRefIndex;
  private final @NotNull KeyCatalogOverBlocks<K> keyCatalog;
  private final @NotNull RecordStorageOverBlocks values;

  private final @Nullable EqualityPolicy<? super V> valueEquality;
  private final @NotNull DataExternalizerEx<V> valueExternalizer;

  private final transient Object lock = new Object();

  private int activeEntryCount;
  private boolean closed;
  private boolean cleaned;

  private DurableMapWithKeyCatalogOverBlocks(@NotNull BlocksStore blocksStore,
                                             @NotNull KeyCatalogOverBlocks<K> keyCatalog,
                                             @NotNull RecordStorageOverBlocks values,
                                             @NotNull RecordRefIndex keyHashToRecordRefIndex,
                                             @Nullable EqualityPolicy<? super V> valueEquality,
                                             @NotNull DataExternalizerEx<V> valueExternalizer) {
    this.blocksStore = blocksStore;
    this.keyCatalog = keyCatalog;
    this.values = values;
    this.keyHashToRecordRefIndex = keyHashToRecordRefIndex;
    this.valueEquality = valueEquality;
    this.valueExternalizer = valueExternalizer;
  }

  public static <K, V> @NotNull DurableMapWithKeyCatalogOverBlocks<K, V> open(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull KeyDescriptorEx<K> keyDescriptor,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull DataExternalizerEx<V> valueExternalizer
  ) throws IOException {
    return open(blocksStore, preferredBlockContentLength, keyDescriptor, valueEquality, valueExternalizer, null);
  }

  public static <K, V, P> @NotNull PatchableDurableMap<K, V, P> openPatchable(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull KeyDescriptorEx<K> keyDescriptor,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> valueExternalizer
  ) throws IOException {
    return asPatchable(
      open(
        blocksStore, preferredBlockContentLength,
        keyDescriptor, valueEquality, valueExternalizer, valueExternalizer
      )
    );
  }

  private static <K, V> @NotNull DurableMapWithKeyCatalogOverBlocks<K, V> open(
    @NotNull BlocksStore blocksStore,
    int preferredBlockContentLength,
    @NotNull KeyDescriptorEx<K> keyDescriptor,
    @Nullable EqualityPolicy<? super V> valueEquality,
    @NotNull DataExternalizerEx<V> valueExternalizer,
    @Nullable PatchableDurableMap.PatchableValueExternalizer<V, ?> patchExternalizer
  ) throws IOException {
    var storeMetadata = blocksStore.storeMetadata();
    DurableMapMetadata mapMetadata;
    if (storeMetadata.version() == 0) {
      mapMetadata = DurableMapMetadata.initial(KEY_VALUE_SEPARATELY);
      blocksStore.updateStoreMetadata(mapMetadata.toStoreMetadata());
    }
    else {
      mapMetadata = DurableMapMetadata.fromStoreMetadata(storeMetadata);
    }

    if (mapMetadata.mapImplementation() != KEY_VALUE_SEPARATELY) {
      throw new CorruptedException("Unexpected durable map implementation " + mapMetadata.mapImplementation());
    }
    if (mapMetadata.mapFormatVersion() != IMPLEMENTATION_VERSION) {
      throw new UnsupportedFormatException("key catalog durable map", IMPLEMENTATION_VERSION, mapMetadata.mapFormatVersion());
    }
    if (mapMetadata.lookupImplementationId() != IMPLEMENTATION_ID) {
      throw new CorruptedException("Unknown lookup implementation " + mapMetadata.lookupImplementationId());
    }
    if (mapMetadata.lookupFormatVersion() != ExtendibleHashMapInt32ToInt64.IMPLEMENTATION_VERSION) {
      throw new UnsupportedFormatException(
        "lookup", ExtendibleHashMapInt32ToInt64.IMPLEMENTATION_VERSION, mapMetadata.lookupFormatVersion()
      );
    }

    var blockCatalog = DurableMapBlockCatalog.open(blocksStore);
    var recordIndexStorage = new ExtendibleHashMapStorageOverLookupBlocks(
      blockCatalog.lookupBlocks(mapMetadata.lookupImplementationId(), mapMetadata.lookupGeneration()),
      ExtendibleHashMapInt32ToInt64.REQUIRED_HEADER_SIZE,
      SEGMENT_SIZE
    );
    var freshLookup = recordIndexStorage.isEmpty();
    ExtendibleHashMapInt32ToInt64 recordRefIndex;
    try {
      recordRefIndex = new ExtendibleHashMapInt32ToInt64(recordIndexStorage);
    }
    catch (IOException | RuntimeException | Error failure) {
      try {
        recordIndexStorage.closeAndClean();
      }
      catch (RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }

    try {
      if (!freshLookup && !recordRefIndex.wasProperlyClosed()) {
        throw new CorruptedException("The key catalog LOOKUP requires recovery");
      }
      var keyCatalog = KeyCatalogOverBlocks.open(blockCatalog, preferredBlockContentLength, recordRefIndex, keyDescriptor);
      var values = RecordStorageOverBlocks.open(blockCatalog, VALUES, preferredBlockContentLength);
      DurableMapWithKeyCatalogOverBlocks<K, V> map;
      if (patchExternalizer == null) {
        map = new DurableMapWithKeyCatalogOverBlocks<>(
          blocksStore, keyCatalog, values, recordRefIndex, valueEquality, valueExternalizer
        );
      }
      else {
        map = new PatchableMap<>(
          blocksStore, keyCatalog, values, recordRefIndex, valueEquality, valueExternalizer, patchExternalizer
        );
      }
      if (freshLookup) {
        var hasKeys = new Ref<>(false);
        keyCatalog.processKeys(_ -> {
          hasKeys.set(true);
          return false;
        });
        if (hasKeys.get()) {
          throw new CorruptedException("The key catalog LOOKUP is missing");
        }
      }
      map.restoreActiveEntryCount();
      return map;
    }
    catch (IOException | RuntimeException | Error failure) {
      try {
        recordRefIndex.closeKeepingDirty();
        if (freshLookup) {
          recordIndexStorage.closeAndClean();
        }
      }
      catch (IOException | RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    synchronized (lock) {
      ensureOpen();
      var keyRecordRef = keyCatalog.findRecordRef(key);
      if (keyRecordRef == NO_VALUE) {
        return false;
      }
      var valueRecordRef = keyCatalog.valueRecordRef(keyRecordRef);
      return isActiveValue(keyRecordRef, valueRecordRef);
    }
  }

  @Override
  public @Nullable V get(@NotNull K key) throws IOException {
    synchronized (lock) {
      ensureOpen();
      var keyRecordRef = keyCatalog.findRecordRef(key);
      if (keyRecordRef == NO_VALUE) {
        return null;
      }
      var valueRecordRef = keyCatalog.valueRecordRef(keyRecordRef);
      return readValue(keyRecordRef, valueRecordRef);
    }
  }

  @Override
  public void put(@NotNull K key, @Nullable V value) throws IOException {
    synchronized (lock) {
      ensureOpen();
      if (value == null) {
        removeLocked(key);
        return;
      }

      var keyRecordRef = keyCatalog.findRecordRef(key);
      var oldValueRecordRef = keyRecordRef == NO_VALUE ? NO_VALUE : keyCatalog.valueRecordRef(keyRecordRef);
      var wasActive = keyRecordRef != NO_VALUE && isActiveValue(keyRecordRef, oldValueRecordRef);
      if (wasActive && valueEquality != null) {
        var oldValue = readValue(keyRecordRef, oldValueRecordRef);
        if (oldValue != null && valueEquality.isEqual(value, oldValue)) {
          return;
        }
      }

      var writer = valueExternalizer.writerFor(value);
      var mutationStarted = false;
      try {
        if (keyRecordRef == NO_VALUE) {
          mutationStarted = true;
          keyRecordRef = keyCatalog.findOrAppend(key);
        }
        else {
          markDirty();
          mutationStarted = true;
        }
        var ownerKeyRecordRef = keyRecordRef;
        var newValueRecordRef = appendValue(writer, record -> ValueRecordLayout.initialize(record, SNAPSHOT, ownerKeyRecordRef));
        keyCatalog.updateValueRecordRef(keyRecordRef, newValueRecordRef);
        if (!wasActive) {
          activeEntryCount++;
        }
      }
      catch (IOException | RuntimeException | Error failure) {
        if (mutationStarted) {
          closeAfterFailure(failure);
        }
        throw failure;
      }
    }
  }

  @Override
  public void remove(@NotNull K key) throws IOException {
    synchronized (lock) {
      ensureOpen();
      removeLocked(key);
    }
  }

  private void removeLocked(@NotNull K key) throws IOException {
    var keyRecordRef = keyCatalog.findRecordRef(key);
    if (keyRecordRef == NO_VALUE) {
      return;
    }
    var oldValueRecordRef = keyCatalog.valueRecordRef(keyRecordRef);
    if (!isActiveValue(keyRecordRef, oldValueRecordRef)) {
      return;
    }

    var mutationStarted = false;
    try {
      markDirty();
      mutationStarted = true;
      var tombstoneRef = values.append(ValueRecordLayout.recordSize(0), record ->
        ValueRecordLayout.initialize(record, TOMBSTONE, keyRecordRef)
      );
      keyCatalog.updateValueRecordRef(keyRecordRef, tombstoneRef);
      activeEntryCount--;
    }
    catch (IOException | RuntimeException | Error failure) {
      if (mutationStarted) {
        closeAfterFailure(failure);
      }
      throw failure;
    }
  }

  protected final <P> void patchValue(@NotNull K key,
                                      @NotNull P patch,
                                      @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> externalizer) throws IOException {
    var writer = externalizer.writerForPatch(patch);
    synchronized (lock) {
      ensureOpen();
      var keyRecordRef = keyCatalog.findRecordRef(key);
      var oldValueRecordRef = keyRecordRef == NO_VALUE ? NO_VALUE : keyCatalog.valueRecordRef(keyRecordRef);
      var wasActive = keyRecordRef != NO_VALUE && isActiveValue(keyRecordRef, oldValueRecordRef);
      var mutationStarted = false;
      try {
        if (keyRecordRef == NO_VALUE) {
          mutationStarted = true;
          keyRecordRef = keyCatalog.findOrAppend(key);
        }
        else {
          markDirty();
          mutationStarted = true;
        }
        var ownerKeyRecordRef = keyRecordRef;
        long newValueRecordRef;
        if (wasActive) {
          newValueRecordRef = appendValue(writer, record -> ValueRecordLayout.initialize(record, PATCH, oldValueRecordRef));
        }
        else {
          newValueRecordRef = appendValue(writer, record -> ValueRecordLayout.initialize(record, SNAPSHOT, ownerKeyRecordRef));
        }
        keyCatalog.updateValueRecordRef(keyRecordRef, newValueRecordRef);
        if (!wasActive) {
          activeEntryCount++;
        }
      }
      catch (IOException | RuntimeException | Error failure) {
        if (mutationStarted) {
          closeAfterFailure(failure);
        }
        throw failure;
      }
    }
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyCatalog.processKeys(processor);
    }
  }

  @Override
  public boolean forEachEntry(@NotNull BiPredicate<? super K, ? super V> processor) throws IOException {
    synchronized (lock) {
      ensureOpen();
      return keyCatalog.processKeyRecords((keyRecordRef, key, valueRecordRef) -> {
        var value = readValue(keyRecordRef, valueRecordRef);
        if (value == null) {
          return true;
        }
        return processor.test(key, value);
      });
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (lock) {
      ensureOpen();
      return activeEntryCount == 0;
    }
  }

  @Override
  public int size() {
    synchronized (lock) {
      ensureOpen();
      return activeEntryCount;
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
      keyHashToRecordRefIndex.flush();
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
      keyHashToRecordRefIndex.close();
      closed = true;
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    synchronized (lock) {
      if (!closed) {
        keyHashToRecordRefIndex.close();
      }
      if (!cleaned) {
        blocksStore.drop();
        cleaned = true;
      }
      closed = true;
    }
  }

  private long appendValue(@NotNull DataExternalizerEx.KnownSizeRecordWriter writer,
                           @NotNull ValueRecordInitializer initializer) throws IOException {
    return values.append(ValueRecordLayout.recordSize(writer.recordSize()), record -> {
      initializer.initialize(record);
      writer.write(record.asSlice(ValueRecordLayout.HEADER_SIZE).asByteBuffer());
    });
  }

  private @Nullable V readValue(long keyRecordRef, long valueRecordRef) throws IOException {
    if (valueRecordRef == NO_VALUE) {
      return null;
    }
    var patches = new ArrayList<MemorySegment>();
    var currentRef = valueRecordRef;
    while (true) {
      var valueRecord = ValueRecordLayout.read(values.read(currentRef));
      switch (valueRecord.kind()) {
        case TOMBSTONE -> {
          requireSameKeyRecordRef(keyRecordRef, valueRecord.reference(), currentRef);
          if (currentRef != valueRecordRef) {
            throw new CorruptedException("A value chain reaches TOMBSTONE " + currentRef);
          }
          return null;
        }
        case PATCH -> {
          var previousRef = valueRecord.reference();
          if (previousRef <= NO_VALUE || previousRef >= currentRef) {
            throw new CorruptedException("Invalid value predecessor: " + currentRef + " -> " + previousRef);
          }
          patches.add(valueRecord.payload());
          currentRef = previousRef;
        }
        case SNAPSHOT -> {
          requireSameKeyRecordRef(keyRecordRef, valueRecord.reference(), currentRef);
          return deserializeValue(valueRecord.payload(), patches);
        }
      }
    }
  }

  private @Nullable V deserializeValue(@NotNull MemorySegment snapshot,
                                       @NotNull ArrayList<MemorySegment> patches) throws IOException {
    if (patches.isEmpty()) {
      return valueExternalizer.read(snapshot.asByteBuffer());
    }
    long totalSize = snapshot.byteSize();
    for (var patch : patches) {
      totalSize = Math.addExact(totalSize, patch.byteSize());
    }
    if (totalSize > Integer.MAX_VALUE) {
      throw new IOException("The patched value exceeds the maximum buffer size: " + totalSize);
    }
    var buffer = ByteBuffer.allocate((int)totalSize);
    buffer.put(snapshot.asByteBuffer());
    for (int i = patches.size() - 1; i >= 0; i--) {
      buffer.put(patches.get(i).asByteBuffer());
    }
    buffer.flip();
    return valueExternalizer.read(buffer);
  }

  private boolean isActiveValue(long keyRecordRef, long valueRecordRef) throws IOException {
    if (valueRecordRef == NO_VALUE) {
      return false;
    }
    var valueRecord = ValueRecordLayout.read(values.read(valueRecordRef));
    return switch (valueRecord.kind()) {
      case SNAPSHOT -> {
        requireSameKeyRecordRef(keyRecordRef, valueRecord.reference(), valueRecordRef);
        yield true;
      }
      case PATCH -> {
        if (valueRecord.reference() <= NO_VALUE || valueRecord.reference() >= valueRecordRef) {
          throw new CorruptedException("Invalid value predecessor: " + valueRecordRef + " -> " + valueRecord.reference());
        }
        yield true;
      }
      case TOMBSTONE -> {
        requireSameKeyRecordRef(keyRecordRef, valueRecord.reference(), valueRecordRef);
        yield false;
      }
    };
  }

  /// Verifies that a snapshot or tombstone belongs to the expected key
  /// @param valueRecordRef used only to format an error message if comparison fails
  private static void requireSameKeyRecordRef(long expectedKeyRecordRef,
                                              long actualKeyRecordRef,
                                              long valueRecordRef) throws CorruptedException {
    if (actualKeyRecordRef != expectedKeyRecordRef) {
      throw new CorruptedException(
        "Value record " + valueRecordRef + " belongs to key " + actualKeyRecordRef + ", expected " + expectedKeyRecordRef
      );
    }
  }

  private void restoreActiveEntryCount() throws IOException {
    synchronized (lock) {
      var count = new int[]{0};
      keyCatalog.processKeyRecords((keyRecordRef, _, valueRecordRef) -> {
        if (isActiveValue(keyRecordRef, valueRecordRef)) {
          count[0]++;
        }
        return true;
      });
      activeEntryCount = count[0];
    }
  }

  private void markDirty() throws IOException {
    keyHashToRecordRefIndex.markDirty();
  }

  private void closeAfterFailure(@NotNull Throwable failure) {
    closed = true;
    try {
      keyHashToRecordRefIndex.closeKeepingDirty();
    }
    catch (IOException | RuntimeException | Error closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The map is already closed");
    }
  }

  @SuppressWarnings("unchecked")
  private static <K, V, P> @NotNull PatchableDurableMap<K, V, P> asPatchable(
    @NotNull DurableMapWithKeyCatalogOverBlocks<K, V> map
  ) {
    return (PatchableDurableMap<K, V, P>)map;
  }

  @FunctionalInterface
  private interface ValueRecordInitializer {
    void initialize(@NotNull MemorySegment record) throws IOException;
  }

  private static final class PatchableMap<K, V, P> extends DurableMapWithKeyCatalogOverBlocks<K, V>
    implements PatchableDurableMap<K, V, P> {
    private final @NotNull PatchableValueExternalizer<V, P> patchExternalizer;

    private PatchableMap(@NotNull BlocksStore blocksStore,
                         @NotNull KeyCatalogOverBlocks<K> keyCatalog,
                         @NotNull RecordStorageOverBlocks values,
                         @NotNull RecordRefIndex keyHashToRecordRefIndex,
                         @Nullable EqualityPolicy<? super V> valueEquality,
                         @NotNull DataExternalizerEx<V> valueExternalizer,
                         @NotNull PatchableValueExternalizer<V, P> patchExternalizer) {
      super(blocksStore, keyCatalog, values, keyHashToRecordRefIndex, valueEquality, valueExternalizer);
      this.patchExternalizer = patchExternalizer;
    }

    @Override
    public void patchValue(@NotNull K key, @NotNull P patch) throws IOException {
      super.patchValue(key, patch, patchExternalizer);
    }
  }
}
