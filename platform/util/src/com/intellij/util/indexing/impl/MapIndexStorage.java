// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value> {
  private static final Logger LOG = Logger.getInstance(MapIndexStorage.class);
  private static final boolean ENABLE_WAL = SystemProperties.getBooleanProperty("idea.index.enable.wal", false);

  protected ValueContainerMap<Key, Value> myMap;
  protected SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  protected final Path myBaseStorageFile;
  protected final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  protected final ReentrantLock l = new ReentrantLock();
  private final DataExternalizer<Value> myDataExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;
  private final boolean myReadOnly;
  private final boolean myEnableWal;
  @NotNull private final ValueContainerInputRemapping myInputRemapping;

  public MapIndexStorage(Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile) throws IOException {
    this(storageFile, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false, false, null);
  }

  public MapIndexStorage(Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile,
                         boolean initialize,
                         boolean readOnly,
                         boolean enableWal,
                         @Nullable ValueContainerInputRemapping inputRemapping) throws IOException {
    myBaseStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    myReadOnly = readOnly;
    myEnableWal = enableWal;
    if (inputRemapping != null) {
      LOG.assertTrue(myReadOnly, "input remapping allowed only for read-only storage");
    } else {
      inputRemapping = ValueContainerInputRemapping.IDENTITY;
    }
    myInputRemapping = inputRemapping;
    if (initialize) initMapAndCache();
  }

  protected void initMapAndCache() throws IOException {
    ValueContainerMap<Key, Value> map = createValueContainerMap();
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/, myKeyDescriptor) {
      @Override
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return map.getModifiableValueContainer(key);
      }

      @Override
      protected void onDropFromCache(final Key key, @NotNull ChangeTrackingValueContainer<Value> valueContainer) {
        assert l.isHeldByCurrentThread();
        try {
          if (!myReadOnly && valueContainer.isDirty()) {
            if (myKeyIsUniqueForIndexedFile) {
              if (valueContainer.containsOnlyInvalidatedChange()) {
                map.remove(key);
                return;
              }
              else if (valueContainer.containsCachedMergedData()) {
                valueContainer.setNeedsCompacting(true);
              }
            }
            map.merge(key, valueContainer);
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    myMap = map;
  }

  protected @NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> createPersistentMap(
    @NotNull KeyDescriptor<Key> keyDescriptor,
    @NotNull DataExternalizer<UpdatableValueContainer<Value>> valueContainerExternalizer,
    boolean isReadOnly,
    boolean compactOnClose,
    boolean keyIsUniqueForIndexedFile) throws IOException {
    PersistentMapImpl<Key, UpdatableValueContainer<Value>> persistentMap;
    PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(Boolean.TRUE);
    if (keyIsUniqueForIndexedFile) {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
    }
    try {
      persistentMap = new PersistentMapImpl<>(PersistentMapBuilder
                                                .newBuilder(getStorageFile(), keyDescriptor, valueContainerExternalizer)
                                                .withWal(myEnableWal && ENABLE_WAL && !isReadOnly)
                                                .setWalExecutor(SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Index Wal Pool"))
                                                .withReadonly(isReadOnly)
                                                .withCompactOnClose(compactOnClose));
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(null);
      if (myKeyIsUniqueForIndexedFile) {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }
    }
    return persistentMap;
  }

  private @NotNull ValueContainerMap<Key, Value> createValueContainerMap() throws IOException {
    ValueContainerExternalizer<Value> valueContainerExternalizer = new ValueContainerExternalizer<>(myDataExternalizer, myInputRemapping);
    PersistentMapBase<Key, UpdatableValueContainer<Value>> persistentMap = createPersistentMap(myKeyDescriptor,
                                                                                               valueContainerExternalizer,
                                                                                               myReadOnly,
                                                                                               compactOnClose(),
                                                                                               myKeyIsUniqueForIndexedFile);

    return new ValueContainerMap<>(persistentMap,
                                   myKeyDescriptor,
                                   myDataExternalizer,
                                   myKeyIsUniqueForIndexedFile);
  }

  @Override
  public void updateValue(Key key, int inputId, Value newValue) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        updateSingleValueDirectly(key, inputId, newValue);
      }
      else {
        IndexStorage.super.updateValue(key, inputId, newValue);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        putSingleValueDirectly(key, inputId, value);
      }
      else {
        read(key).addValue(inputId, value);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        removeSingleValueDirectly(key, inputId);
      }
      else {
        // important: assuming the key exists in the index
        read(key).removeAssociatedValue(inputId);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  private Path getStorageFile() {
    return getIndexStorageFile(myBaseStorageFile);
  }

  @Override
  public void flush() throws IOException {
    ConcurrencyUtil.withLock(l, () -> {
      if (!myMap.isClosed()) {
        myCache.clear();
        if (myMap.isDirty()) myMap.force();
      }
    });
  }

  protected boolean compactOnClose() {
    return false;
  }

  @Override
  public void close() throws StorageException {
    try {
      flush();
      myMap.close();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      unwrapCauseAndRethrow(e);
    }
  }

  @Override
  public void clear() throws StorageException{
    try {
      myMap.closeAndDelete();
    }
    catch (Exception ignored) { }
    try {
      initMapAndCache();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      unwrapCauseAndRethrow(e);
    }
  }

  @Override
  @NotNull
  public ChangeTrackingValueContainer<Value> read(final Key key) throws StorageException {
    return ConcurrencyUtil.withLock(l, () -> {
      try {
        return myCache.get(key);
      }
      catch (RuntimeException e) {
        return unwrapCauseAndRethrow(e);
      }
    });
  }

  private void removeSingleValueDirectly(Key key, int inputId) throws IOException {
    assert myKeyIsUniqueForIndexedFile;
    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      return;
    }

    myMap.remove(key);
  }

  private void updateSingleValueDirectly(Key key, int inputId, Value newValue) throws IOException {
    assert myKeyIsUniqueForIndexedFile;
    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      cached.addValue(inputId, newValue);
      return;
    }

    ChangeTrackingValueContainer<Value> valueContainer = new ChangeTrackingValueContainer<>(null);
    valueContainer.addValue(inputId, newValue);
    myMap.merge(key, valueContainer);
  }

  private void putSingleValueDirectly(Key key, int inputId, Value value) throws IOException {
    assert myKeyIsUniqueForIndexedFile;
    ChangeTrackingValueContainer<Value> cached;
    cached = readIfCached(key);

    if (cached != null) {
      cached.addValue(inputId, value);
      return;
    }
    // do not pollute the cache with keys unique to indexed file
    ChangeTrackingValueContainer<Value> valueContainer = new ChangeTrackingValueContainer<>(null);
    valueContainer.addValue(inputId, value);
    myMap.merge(key, valueContainer);
  }

  @Nullable
  private ChangeTrackingValueContainer<Value> readIfCached(Key key) {
    return ConcurrencyUtil.withLock(l, () -> myCache.getIfCached(key));
  }

  private static void assertKeyInputIdConsistency(@NotNull Object key, int inputId) {
    assert ((Integer)key).intValue() == inputId;
  }

  @Override
  public void clearCaches() {
    ConcurrencyUtil.withLock(l, () -> {
      for(Map.Entry<Key, ChangeTrackingValueContainer<Value>> entry:myCache.entrySet()) {
        entry.getValue().dropMergedData();
      }
    });
  }

  @ApiStatus.Internal
  public void clearCachedMappings() {
    ConcurrencyUtil.withLock(l, () -> myCache.clear());
  }

  protected static <T> T unwrapCauseAndRethrow(RuntimeException e) throws StorageException {
    final Throwable cause = e.getCause();
    if (cause instanceof IOException) {
      throw new StorageException(cause);
    }
    if (cause instanceof StorageException) {
      throw (StorageException)cause;
    }
    throw e;
  }

  @TestOnly
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws StorageException {
    return ConcurrencyUtil.withLock(l, () -> {
      try {
        myCache.clear(); // this will ensure that all new keys are made into the map
        return doProcessKeys(processor);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      catch (RuntimeException e) {
        unwrapCauseAndRethrow(e);
        return false;
      }
    });
  }

  protected boolean doProcessKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return myMap.processKeys(processor);
  }

  @TestOnly
  public PersistentMapBase<Key, UpdatableValueContainer<Value>> getIndexMap() {
    return myMap.getStorageMap();
  }

  @NotNull
  public static Path getIndexStorageFile(@NotNull Path baseFile) {
    return baseFile.resolveSibling(baseFile.getFileName() + ".storage");
  }
}
