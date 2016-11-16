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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.Processor;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class MapReduceIndexBase<Key,Value, Input> implements AbstractUpdatableIndex<Key, Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  @Nullable protected final ID<Key, Value> myIndexId;
  protected final DataIndexer<Key, Value, Input> myIndexer;
  @NotNull protected final IndexStorage<Key, Value> myStorage;

  protected final DataExternalizer<Value> myValueExternalizer;
  protected final IndexExtension<Key, Value, Input> myExtension;
  private final AtomicLong myModificationStamp = new AtomicLong();

  @Nullable protected PersistentHashMap<Integer, Collection<Key>> myInputsIndex;

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private final LowMemoryWatcher myLowMemoryFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      try {
        if (myStorage instanceof MemoryIndexStorageBase) {
          Lock writeLock = getWriteLock();
          if (writeLock.tryLock()) {
            try {
              ((MemoryIndexStorageBase<?, ?, ?>)myStorage).clearCaches();
            } finally {
              writeLock.unlock();
            }
          }
        }
        flush();
      } catch (StorageException e) {
        LOG.info(e);
        requestRebuild(e);
      }
    }
  });

  protected MapReduceIndexBase(IndexExtension<Key, Value, Input> extension,
                               @NotNull IndexStorage<Key, Value> storage,
                               boolean createInputIndex) throws IOException {
    myIndexId = extension.getName();
    myExtension = extension;
    myIndexer = extension.getIndexer();
    myStorage = storage;
    myValueExternalizer = extension.getValueExternalizer();

    if (createInputIndex) {
      myInputsIndex = createInputsIndex();
    }

  }

  protected PersistentHashMap<Integer, Collection<Key>> createInputsIndex() throws IOException {
    return null;
  }

  protected <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension,
                                                                                   ID<K, ?> indexId,
                                                                                   KeyDescriptor<K> keyDescriptor) {
    DataExternalizer<Collection<K>> externalizer;
    externalizer = new InputIndexDataExternalizer<K>(keyDescriptor, indexId);
    return externalizer;
  }

  @NotNull
  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  @Override
  public void clear() throws StorageException {
    try {
      getWriteLock().lock();
      myStorage.clear();
      if (myInputsIndex != null) {
        cleanMapping(myInputsIndex);
        myInputsIndex = createInputsIndex();
      }
      cleanMappings();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  protected void cleanMappings() throws IOException {

  }

  protected static void cleanMapping(@NotNull PersistentHashMap<?, ?> index) {
    final File baseFile = index.getBaseFile();
    try {
      index.close();
    }
    catch (Throwable ignored) {
    }

    IOUtil.deleteAllFilesStartingWith(baseFile);
  }

  @Override
  public void flush() throws StorageException{
    try {
      getReadLock().lock();
      doForce(myInputsIndex);
      doForce();
      myStorage.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        throw new StorageException(cause);
      }
      else {
        throw e;
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  protected void doForce() {
  }

  protected static void doForce(PersistentHashMap<?, ?> inputsIndex) {
    if (inputsIndex != null && inputsIndex.isDirty()) {
      inputsIndex.force();
    }
  }

  @Override
  public void dispose() {
    myLowMemoryFlusher.stop();
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      try {
        myStorage.close();
      }
      finally {
        doClose(myInputsIndex);
        doClose();
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  protected void doClose() {
  }

  protected static void doClose(PersistentHashMap<?, ?> index) {
    if (index != null) {
      try {
        index.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  @Override
  public final Lock getReadLock() {
    return myLock.readLock();
  }

  @NotNull
  @Override
  public final Lock getWriteLock() {
    return myLock.writeLock();
  }

  @Override
  @NotNull
  public ValueContainer<Value> getData(@NotNull final Key key) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      ValueContainerImpl.ourDebugIndexInfo.set(myIndexId);
      return myStorage.read(key);
    }
    finally {
      ValueContainerImpl.ourDebugIndexInfo.set(null);
      lock.unlock();
    }
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<Key> processor) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.processKeys(processor);
    }
    finally {
      lock.unlock();
    }
  }

  @NotNull
  @Override
  public Computable<Boolean> update(final int inputId, @Nullable Input content) {
    Map<Key, Value> data = content != null ? myIndexer.map(content) : Collections.<Key, Value>emptyMap();

    checkValuesHaveProperEqualsAndHashCode(data);

    myStorage.checkCanceled();

    // do not depend on content!
    final UpdateData<Key, Value> updateData = buildUpdateData(data,
                                                              createOldKeysGetterByInputIndex(inputId),
                                                              inputId);
    return new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        try {
          updateWithMap(inputId, updateData);
        }
        catch (StorageException ex) {
          LOG.info("Exception during updateWithMap:" + ex);
          requestRebuild(ex);
          return Boolean.FALSE;
        }
        catch (RuntimeException ex) {
          if (getProcessCanceledExceptionClass().isInstance(ex)) {
            LOG.info("Exception during updateWithMap:" + ex);
            requestRebuild(ex);
            return Boolean.FALSE;
          } else {
            throw ex;
          }
        }

        return Boolean.TRUE;
      }
    };
  }

  @NotNull
  protected NotNullComputable<Collection<Key>> createOldKeysGetterByInputIndex(final int inputId) {
    return new NotNullComputable<Collection<Key>>() {
      @NotNull
      @Override
      public Collection<Key> compute() {
        try {
          Collection<Key> oldKeys = MapReduceIndexBase.this.readInputKeys(inputId);
          return oldKeys == null ? Collections.<Key>emptyList() : oldKeys;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  protected UpdateData<Key, Value> buildUpdateData(Map<Key, Value> data, NotNullComputable<Collection<Key>> oldKeysGetter, int savedInputId) {
    return new SimpleUpdateData(myIndexId, savedInputId, data, oldKeysGetter);
  }

  protected Collection<Key> readInputKeys(int inputId) throws IOException {
    return myInputsIndex != null ? myInputsIndex.get(inputId) : null;
  }

  protected void saveInputKeys(int inputId, int savedInputId, Map<Key, Value> newData) throws IOException {
    if (myInputsIndex != null) {
      if (newData.size() > 0) {
        myInputsIndex.put(inputId, newData.keySet());
      }
      else {
        myInputsIndex.remove(inputId);
      }
    }
  }

  protected void checkValuesHaveProperEqualsAndHashCode(Map<Key, Value> data) {
    if (DebugAssertions.DEBUG) {
      for (Map.Entry<Key, Value> e : data.entrySet()) {
        final Value value = e.getValue();
        if (!(Comparing.equal(value, value) && (value == null || value.hashCode() == value.hashCode()))) {
          LOG.error("Index " + myIndexId.toString() + " violates equals / hashCode contract for Value parameter");
        }

        if (myValueExternalizer != null) {
          try {
            final BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
            DataOutputStream outputStream = new DataOutputStream(out);
            myValueExternalizer.save(outputStream, value);
            outputStream.close();
            final Value deserializedValue =
              myValueExternalizer.read(new DataInputStream(new UnsyncByteArrayInputStream(out.getInternalBuffer(), 0, out.size())));

            if (!(Comparing.equal(value, deserializedValue) && (value == null || value.hashCode() == deserializedValue.hashCode()))) {
              LOG.error("Index " + myIndexId.toString() + " deserialization violates equals / hashCode contract for Value parameter");
            }
          }
          catch (IOException ex) {
            LOG.error(ex);
          }
        }
      }
    }
  }

  public IndexExtension<Key, Value, Input> getExtension() {
    return myExtension;
  }

  public long getModificationStamp() {
    return myModificationStamp.get();
  }

  public class SimpleUpdateData extends UpdateData<Key, Value> {
    private final int savedInputId;
    private final @NotNull Map<Key, Value> newData;
    protected final @NotNull NotNullComputable<Collection<Key>> oldKeysGetter;

    public SimpleUpdateData(ID<Key,Value> indexId, int id, @NotNull Map<Key, Value> data, @NotNull NotNullComputable<Collection<Key>> getter) {
      super(indexId);
      savedInputId = id;
      newData = data;
      oldKeysGetter = getter;
    }

    public void iterateRemovedOrUpdatedKeys(int inputId, RemovedOrUpdatedKeyProcessor<Key> consumer) throws StorageException {
      MapDiffUpdateData.iterateRemovedKeys(oldKeysGetter.compute(), inputId, consumer);
    }

    public void iterateAddedKeys(final int inputId, final AddedKeyProcessor<Key, Value> consumer) throws StorageException {
      MapDiffUpdateData.iterateAddedKeyAndValues(inputId, consumer, newData);
    }

    @Override
    public void save(int inputId) throws IOException {
      saveInputKeys(inputId, savedInputId, newData);
    }

    public @NotNull Map<Key, Value> getNewData() {
      return newData;
    }
  }

  private final MapDiffUpdateData.RemovedOrUpdatedKeyProcessor<Key>
    myRemoveStaleKeyOperation = new MapDiffUpdateData.RemovedOrUpdatedKeyProcessor<Key>() {
    @Override
    public void process(Key key, int inputId) throws StorageException {
      myModificationStamp.incrementAndGet();
      myStorage.removeAllValues(key, inputId);
    }
  };

  private final MapDiffUpdateData.AddedKeyProcessor<Key, Value> myAddedKeyProcessor = new MapDiffUpdateData.AddedKeyProcessor<Key, Value>() {
    @Override
    public void process(Key key, Value value, int inputId) throws StorageException {
      myModificationStamp.incrementAndGet();
      myStorage.addValue(key, inputId, value);
    }
  };

  protected void updateWithMap(final int inputId,
                               @NotNull UpdateData<Key, Value> updateData) throws StorageException {
    getWriteLock().lock();
    try {
      try {
        ValueContainerImpl.ourDebugIndexInfo.set(myIndexId);
        updateData.iterateRemovedOrUpdatedKeys(inputId, myRemoveStaleKeyOperation);
        updateData.iterateAddedKeys(inputId, myAddedKeyProcessor);
        updateData.save(inputId);
      }
      catch (RuntimeException e) {
        if (getProcessCanceledExceptionClass().isInstance(e)) {
          throw e;
        } else {
          throw new StorageException(e);
        }
      }
      catch (Throwable e) { // e.g. IOException, AssertionError
        throw new StorageException(e);
      }
      finally {
        ValueContainerImpl.ourDebugIndexInfo.set(null);
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }
}

