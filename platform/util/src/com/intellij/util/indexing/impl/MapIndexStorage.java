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
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>, IndexStorage.Dumpable {
  private static final Logger LOG = Logger.getInstance(MapIndexStorage.class);
  protected PersistentMap<Key, UpdatableValueContainer<Value>> myMap;
  protected SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  protected final File myBaseStorageFile;
  protected final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  protected final Lock l = new ReentrantLock();
  private final DataExternalizer<Value> myDataExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;
  private final boolean myReadOnly;

  protected MapIndexStorage(@NotNull File storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile) throws IOException {
    this(storageFile, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false);
  }

  protected MapIndexStorage(@NotNull File storageFile,
                            @NotNull KeyDescriptor<Key> keyDescriptor,
                            @NotNull DataExternalizer<Value> valueExternalizer,
                            final int cacheSize,
                            boolean keyIsUniqueForIndexedFile,
                            boolean initialize,
                            boolean readOnly) throws IOException {
    myBaseStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    myReadOnly = readOnly;
    if (initialize) initMapAndCache();
  }

  @Override
  public void dump() {
    ((PersistentHashMap)myMap).dump();
  }

  protected void initMapAndCache() throws IOException {
    final Object lock;
    if (PersistentMap.useIndexServer) {
      myMap = new TCPPersistentMap<Key, UpdatableValueContainer<Value>>(getStorageFile(), myKeyDescriptor, new ValueContainerMap.ValueContainerExternalizer<Value>(myDataExternalizer));
      lock = new Object();
    }
    else {
      PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(
        new PersistentHashMapValueStorage.ExceptionalIOCancellationCallback() {
          @Override
          public void checkCancellation() {
            checkCanceled();
          }
        });
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(Boolean.TRUE);
      try {
        myMap = new ValueContainerMap<Key, Value>(getStorageFile(),
                                                  myKeyDescriptor,
                                                  myDataExternalizer,
                                                  myKeyIsUniqueForIndexedFile,
                                                  myReadOnly);
      }
      finally {
        PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(null);
        PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(null);
      }
      lock = null;
    }
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/) {
      @Override
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Initializer<Value>() {
          @NotNull
          @Override
          public Object getLock() {
            if (PersistentMap.useIndexServer) {
              return lock;
            } else {
              return ((ValueContainerMap)myMap).getDataAccessLock();
            }
          }

          @Override
          public ValueContainer<Value> compute() {
            ValueContainer<Value> value;
            try {
              value = myMap.get(key);
              if (value == null) {
                value = new ValueContainerImpl<Value>();
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
      protected void onDropFromCache(final Key key, @NotNull final ChangeTrackingValueContainer<Value> valueContainer) {
        if (!myReadOnly && valueContainer.isDirty()) {
          try {
            myMap.put(key, valueContainer);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };
  }

  protected abstract void checkCanceled();

  @NotNull
  private File getStorageFile() {
    return new File(myBaseStorageFile.getPath() + ".storage");
  }

  @Override
  public void flush() {
    l.lock();
    try {
      if (!myMap.isClosed()) {
        myCache.clear();
        if (myMap.isDirty()) myMap.force();
      }
    }
    finally {
      l.unlock();
    }
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
    catch (IOException e) {
      LOG.error(e);
    }
    catch (RuntimeException e) {
      LOG.error(e);
    }
    try {
      IOUtil.deleteAllFilesStartingWith(getStorageFile());
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
    l.lock();
    try {
      return myCache.get(key);
    }
    catch (RuntimeException e) {
      return unwrapCauseAndRethrow(e);
    }
    finally {
      l.unlock();
    }
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (!myKeyIsUniqueForIndexedFile) {
        read(key).addValue(inputId, value);
        return;
      }

      ChangeTrackingValueContainer<Value> cached;
      try {
        l.lock();
        cached = myCache.getIfCached(key);
      } finally {
        l.unlock();
      }

      if (cached != null) {
        cached.addValue(inputId, value);
        return;
      }
      // do not pollute the cache with keys unique to indexed file
      ChangeTrackingValueContainer<Value> valueContainer = new ChangeTrackingValueContainer<Value>(null);
      valueContainer.addValue(inputId, value);
      myMap.put(key, valueContainer);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    try {
      myMap.markDirty();
      // important: assuming the key exists in the index
      read(key).removeAssociatedValue(inputId);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void clearCaches() {
    l.lock();
    try {
      for(Map.Entry<Key, ChangeTrackingValueContainer<Value>> entry:myCache.entrySet()) {
        entry.getValue().dropMergedData();
      }
    } finally {
      l.unlock();
    }
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
  public boolean processKeys(@NotNull Processor<Key> processor) throws StorageException {
    l.lock();
    try {
      myCache.clear(); // this will ensure that all new keys are made into the map
      return myMap.processKeys(processor);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      unwrapCauseAndRethrow(e);
      return false;
    }
    finally {
      l.unlock();
    }
  }
}
