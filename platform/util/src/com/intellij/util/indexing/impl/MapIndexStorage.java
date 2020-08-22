/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
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
  protected PersistentMap<Key, UpdatableValueContainer<Value>> myMap;
  protected SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  protected final Path myBaseStorageFile;
  protected final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  protected final ReentrantLock l = new ReentrantLock();
  private final DataExternalizer<Value> myDataExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;
  private final boolean myReadOnly;
  @NotNull private final ValueContainerInputRemapping myInputRemapping;

  public MapIndexStorage(@NotNull Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile) throws IOException {
    this(storageFile, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false, null);
  }

  public MapIndexStorage(@NotNull Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile,
                         boolean initialize,
                         boolean readOnly,
                         @Nullable ValueContainerInputRemapping inputRemapping) throws IOException {
    myBaseStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    myReadOnly = readOnly;
    if (inputRemapping != null) {
      LOG.assertTrue(myReadOnly, "input remapping allowed only for read-only storage");
    } else {
      inputRemapping = ValueContainerInputRemapping.IDENTITY;
    }
    myInputRemapping = inputRemapping;
    if (initialize) initMapAndCache();
  }

  protected void initMapAndCache() throws IOException {
    final ValueContainerMap<Key, Value> map;
    PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(
      new PersistentHashMapValueStorage.ExceptionalIOCancellationCallback() {
        @Override
        public void checkCancellation() {
          checkCanceled();
        }
      });
    PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(Boolean.TRUE);
    if (myKeyIsUniqueForIndexedFile) {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
    }
    try {
      map = new ValueContainerMap<Key, Value>(getStorageFile(), myKeyDescriptor, myDataExternalizer, myKeyIsUniqueForIndexedFile, myInputRemapping) {
        @Override
        protected boolean isReadOnly() {
          return myReadOnly;
        }
      };
    } finally {
      PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(null);
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(null);
      if (myKeyIsUniqueForIndexedFile) {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }
    }
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/, myKeyDescriptor) {
      @Override
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<>(new ChangeTrackingValueContainer.Initializer<Value>() {
          @Override
          public @NotNull Object getLock() {
            return map.getDataAccessLock();
          }

          @NotNull
          @Override
          public ValueContainer<Value> compute() {
            ValueContainer<Value> value;
            try {
              value = map.get(key);
              if (value == null) {
                value = new ValueContainerImpl<>();
              }
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            return value;
          }
        });
      }

      @Override
      protected void onDropFromCache(final Key key, @NotNull ChangeTrackingValueContainer<Value> valueContainer) {
        assert l.isHeldByCurrentThread();
        ChangeTrackingValueContainer<Value> storedContainer = valueContainer;
        if (!myReadOnly && valueContainer.isDirty()) {
          if (myKeyIsUniqueForIndexedFile) {
            if (valueContainer.containsOnlyInvalidatedChange()) {
              storedContainer = new ChangeTrackingValueContainer<>(null);
            }
            else if (storedContainer.containsCachedMergedData()) {
              storedContainer.setNeedsCompacting(true);
            }
          }
          try {
            map.put(key, storedContainer);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };

    myMap = map;
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

  protected void checkCanceled() {
    // Do nothing by default.
  }

  @NotNull
  private Path getStorageFile() {
    return getIndexStorageFile(myBaseStorageFile);
  }

  @Override
  public void flush() {
    ConcurrencyUtil.withLock(l, () -> {
      if (!myMap.isClosed()) {
        myCache.clear();
        if (myMap.isDirty()) myMap.force();
      }
    });
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
      myMap.close();
    }
    catch (Exception ignored) { }
    try {
      IOUtil.deleteAllFilesStartingWith(getStorageFile().toFile());
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

    myMap.put(key, new ChangeTrackingValueContainer<>(null));
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
    myMap.put(key, valueContainer);
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
    myMap.put(key, valueContainer);
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
    return myMap instanceof PersistentHashMap && myKeyDescriptor instanceof InlineKeyDescriptor
           // process keys and check that they're already present in map because we don't have separated key storage we must check keys
           ? ((PersistentHashMap<Key, UpdatableValueContainer<Value>>)myMap).processKeysWithExistingMapping(processor)
           // optimization: process all keys, some of them might be already deleted but we don't care. We just read key storage file here
           : myMap.processKeys(processor);
  }

  @TestOnly
  public PersistentMap<Key, UpdatableValueContainer<Value>> getIndexMap() {
    return myMap;
  }

  @NotNull
  public static Path getIndexStorageFile(@NotNull Path baseFile) {
    return baseFile.resolveSibling(baseFile.getFileName() + ".storage");
  }
}
