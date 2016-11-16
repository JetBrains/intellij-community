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
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public abstract class MemoryIndexStorageBase<Key, Value, Storage extends IndexStorage<Key, Value>> implements IndexStorage<Key, Value> {
  private final static Logger LOG = Logger.getInstance(MemoryIndexStorageBase.class);

  private final Map<Key, ChangeTrackingValueContainer<Value>> myMap = new HashMap<Key, ChangeTrackingValueContainer<Value>>();
  @NotNull
  protected final Storage myBackendStorage;
  private final List<BufferingStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ID<?, ?> myIndexId;
  private boolean myBufferingEnabled;

  public interface BufferingStateListener {
    void bufferingStateChanged(boolean newState);

    void memoryStorageCleared();
  }

  public MemoryIndexStorageBase(@NotNull Storage backend, ID<?, ?> indexId) {
    myBackendStorage = backend;
    myIndexId = indexId;
  }

  @NotNull
  public IndexStorage<Key, Value> getBackendStorage() {
    return myBackendStorage;
  }

  public void addBufferingStateListener(@NotNull BufferingStateListener listener) {
    myListeners.add(listener);
  }

  public void removeBufferingStateListener(@NotNull BufferingStateListener listener) {
    myListeners.remove(listener);
  }

  public void setBufferingEnabled(boolean enabled) {
    final boolean wasEnabled = myBufferingEnabled;
    assert wasEnabled != enabled;

    myBufferingEnabled = enabled;
    for (BufferingStateListener listener : myListeners) {
      listener.bufferingStateChanged(enabled);
    }
  }

  public boolean isBufferingEnabled() {
    return myBufferingEnabled;
  }

  public void clearMemoryMap() {
    myMap.clear();
  }

  public void fireMemoryStorageCleared() {
    for (BufferingStateListener listener : myListeners) {
      listener.memoryStorageCleared();
    }
  }

  void clearCaches() {
    if (myMap.size() == 0) return;

    if (DebugAssertions.DEBUG) {
      String message = "Dropping caches for " + (myIndexId != null ? myIndexId:this) + ", number of items:" + myMap.size();
      LOG.info(message);
    }

    for(ChangeTrackingValueContainer<Value> v:myMap.values()) {
      v.dropMergedData();
    }
  }

  @Override
  public void close() throws StorageException {
    myBackendStorage.close();
  }

  @Override
  public void clear() throws StorageException {
    clearMemoryMap();
    myBackendStorage.clear();
  }

  @Override
  public void flush() throws IOException {
    myBackendStorage.flush();
  }

  @NotNull
  @Override
  public Collection<Key> getKeys() throws StorageException {
    final Set<Key> keys = new HashSet<Key>();
    processKeys(new CancelableCollectProcessor<Key>(keys, new Runnable() {
      @Override
      public void run() {
        checkCanceled();
      }
    }));
    return keys;
  }

  @Override
  public boolean processKeys(@NotNull final Processor<Key> processor) throws StorageException {
    final Processor<Key> uniqueResultProcessor = processCacheFirstProcessor(processor);
    return uniqueResultProcessor != null && myBackendStorage.processKeys(uniqueResultProcessor);
  }

  @Nullable
  protected Processor<Key> processCacheFirstProcessor(final Processor<Key> initialProcessor) {
    if (myMap.isEmpty()) return initialProcessor;
    final Set<Key> stopList = new HashSet<Key>();

    Processor<Key> decoratingProcessor = new Processor<Key>() {
      @Override
      public boolean process(Key key) {
        if (stopList.contains(key)) return true;

        final UpdatableValueContainer<Value> container = myMap.get(key);
        if (container != null && container.size() == 0) {
          return true;
        }
        return initialProcessor.process(key);
      }
    };

    for (Key key : myMap.keySet()) {
      if (!decoratingProcessor.process(key)) {
        return null;
      }
      stopList.add(key);
    }

    return decoratingProcessor;
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myBufferingEnabled) {
      getMemValueContainer(key).addValue(inputId, value);
      return;
    }
    final ChangeTrackingValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    myBackendStorage.addValue(key, inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    if (myBufferingEnabled) {
      getMemValueContainer(key).removeAssociatedValue(inputId);
      return;
    }
    final ChangeTrackingValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    myBackendStorage.removeAllValues(key, inputId);
  }

  private UpdatableValueContainer<Value> getMemValueContainer(final Key key) {
    ChangeTrackingValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer == null) {
      valueContainer = new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Initializer<Value>() {
        @Override
        public Object getLock() {
          return this;
        }

        @Override
        public ValueContainer<Value> compute() {
          try {
            return myBackendStorage.read(key);
          }
          catch (StorageException e) {
            throw new RuntimeException(e);
          }
        }
      });
      myMap.put(key, valueContainer);
    }
    return valueContainer;
  }

  @Override
  @NotNull
  public ValueContainer<Value> read(final Key key) throws StorageException {
    final ValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      return valueContainer;
    }

    return myBackendStorage.read(key);
  }
}
