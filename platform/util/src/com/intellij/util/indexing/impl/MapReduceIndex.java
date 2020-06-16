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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.impl.forward.IntForwardIndexAccessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class MapReduceIndex<Key,Value, Input> implements InvertedIndex<Key, Value, Input> {
  private static final Logger LOG = Logger.getInstance(MapReduceIndex.class);
  @NotNull protected final IndexId<Key, Value> myIndexId;
  @NotNull protected final IndexStorage<Key, Value> myStorage;

  protected final DataExternalizer<Value> myValueExternalizer;
  protected final IndexExtension<Key, Value, Input> myExtension;
  protected final AtomicLong myModificationStamp = new AtomicLong();
  protected final DataIndexer<Key, Value, Input> myIndexer;

  private final ForwardIndex myForwardIndex;
  private final ForwardIndexAccessor<Key, Value> myForwardIndexAccessor;

  @NotNull
  private final ReadWriteLock myLock;
  private final boolean myUseIntForwardIndex;
  private volatile boolean myDisposed;

  private final LowMemoryWatcher myLowMemoryFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      try {
        myLock.readLock().lock();
        try {
          myStorage.clearCaches();
        }
        finally {
          myLock.readLock().unlock();
        }

        flush();
      }
      catch (Throwable e) {
        requestRebuild(e);
      }
    }
  });

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                           @Nullable ReadWriteLock lock) {
    myIndexId = extension.getName();
    myExtension = extension;
    myIndexer = myExtension.getIndexer();
    myStorage = storage;
    myValueExternalizer = extension.getValueExternalizer();
    myForwardIndex = forwardIndex;
    myForwardIndexAccessor = forwardIndexAccessor;
    myUseIntForwardIndex = forwardIndex instanceof IntForwardIndex && forwardIndexAccessor instanceof IntForwardIndexAccessor;
    LOG.assertTrue(forwardIndex instanceof IntForwardIndex == forwardIndexAccessor instanceof IntForwardIndexAccessor, "Invalid index configuration");
    myLock = lock == null ? new ReentrantReadWriteLock() : lock;
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) {
    this(extension, storage, forwardIndex, forwardIndexAccessor,  null);
  }

  public ForwardIndex getForwardIndex() {
    return myForwardIndex;
  }

  public ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() {
    return myForwardIndexAccessor;
  }

  @NotNull
  public IndexExtension<Key, Value, Input> getExtension() {
    return myExtension;
  }

  @NotNull
  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  @NotNull
  public final ReadWriteLock getLock() {
    return myLock;
  }

  @Override
  public void clear() {
    myLock.writeLock().lock();
    try {
      myModificationStamp.incrementAndGet();
      doClear();
    }
    catch (StorageException | IOException e) {
      LOG.error(e);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  protected void doClear() throws StorageException, IOException {
    myStorage.clear();
    if (myForwardIndex != null) myForwardIndex.clear();
  }

  @Override
  public void flush() throws StorageException{
    myLock.readLock().lock();
    try {
      doFlush();
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
      myLock.readLock().unlock();
    }
  }

  protected void doFlush() throws IOException, StorageException {
    if (myForwardIndex != null) myForwardIndex.force();
    myStorage.flush();
  }

  @Override
  public void dispose() {
    myLowMemoryFlusher.stop();
    myLock.writeLock().lock();
    try {
      doDispose();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      myDisposed = true;
      myLock.writeLock().unlock();
    }
  }

  protected void doDispose() throws StorageException {
    try {
      myStorage.close();
    }
    finally {
      try {
        if (myForwardIndex != null) myForwardIndex.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  @NotNull
  public ValueContainer<Value> getData(@NotNull final Key key) throws StorageException {
    myLock.readLock().lock();
    try {
      if (myDisposed) {
        return new ValueContainerImpl<>();
      }
      IndexDebugProperties.DEBUG_INDEX_ID.set(myIndexId);
      return myStorage.read(key);
    }
    finally {
      IndexDebugProperties.DEBUG_INDEX_ID.set(null);
      myLock.readLock().unlock();
    }
  }

  @Override
  public final @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable Input content) throws MapInputException, ProcessCanceledException {
    InputData<Key, Value> data = mapInput(inputId, content);

    UpdateData<Key, Value> updateData = new UpdateData<>(
      inputId,
      data.getKeyValues(),
      () -> getKeysDiffBuilder(inputId),
      myIndexId,
      () -> updateForwardIndex(inputId, data)
    );

    return () -> {
      try {
        updateWithMap(updateData);
      }
      catch (StorageException | ProcessCanceledException ex) {
        String message = "An exception during updateWithMap(). Index " + myIndexId.getName() + " will be rebuilt.";
        //noinspection InstanceofCatchParameter
        if (ex instanceof ProcessCanceledException) {
          LOG.error(message, ex);
        } else {
          LOG.info(message, ex);
        }
        requestRebuild(ex);
        return false;
      }
      return true;
    };
  }

  public static final class MapInputException extends RuntimeException {
    public MapInputException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  protected void updateForwardIndex(int inputId, @NotNull InputData<Key, Value> data) throws IOException {
    if (myForwardIndex != null) {
      if (myUseIntForwardIndex) {
        ((IntForwardIndex)myForwardIndex).putInt(inputId, ((IntForwardIndexAccessor<Key, Value>)myForwardIndexAccessor).serializeIndexedDataToInt(data));
      } else {
        myForwardIndex.put(inputId, myForwardIndexAccessor.serializeIndexedData(data));
      }
    }
  }

  @NotNull
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (myForwardIndex != null) {
      if (myUseIntForwardIndex) {
        return ((IntForwardIndexAccessor<Key, Value>)myForwardIndexAccessor).getDiffBuilderFromInt(inputId, ((IntForwardIndex)myForwardIndex).getInt(inputId));
      } else {
        return myForwardIndexAccessor.getDiffBuilder(inputId, myForwardIndex.get(inputId));
      }
    }
    return new EmptyInputDataDiffBuilder<>(inputId);
  }

  @NotNull
  protected InputData<Key, Value> mapInput(int inputId, @Nullable Input content) {
    if (content == null) {
      return InputData.empty();
    }
    Map<Key, Value> data = mapByIndexer(inputId,  content);
    checkValuesHaveProperEqualsAndHashCode(data, myIndexId, myValueExternalizer);
    checkCanceled();
    return new InputData<>(data);
  }

  @NotNull
  protected Map<Key, Value> mapByIndexer(int inputId, @NotNull Input content) {
    try {
      return myIndexer.map(content);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new MapInputException("Failed to map data for input " + inputId + " for index " + myIndexId.getName(), e);
    }
  }

  public abstract void checkCanceled();

  protected abstract void requestRebuild(@NotNull Throwable e);

  public long getModificationStamp() {
    return myModificationStamp.get();
  }

  private final RemovedKeyProcessor<Key>
    myRemovedKeyProcessor = new RemovedKeyProcessor<Key>() {
    @Override
    public void process(Key key, int inputId) throws StorageException {
      myModificationStamp.incrementAndGet();
      myStorage.removeAllValues(key, inputId);
    }
  };

  private final KeyValueUpdateProcessor<Key, Value> myAddedKeyProcessor = new KeyValueUpdateProcessor<Key, Value>() {
    @Override
    public void process(Key key, Value value, int inputId) throws StorageException {
      myModificationStamp.incrementAndGet();
      myStorage.addValue(key, inputId, value);
    }
  };

  private final KeyValueUpdateProcessor<Key, Value> myUpdatedKeyProcessor = new KeyValueUpdateProcessor<Key, Value>() {
    @Override
    public void process(Key key, Value value, int inputId) throws StorageException {
      myModificationStamp.incrementAndGet();
      myStorage.removeAllValues(key, inputId);
      myStorage.addValue(key, inputId, value);
    }
  };

  public void updateWithMap(@NotNull AbstractUpdateData<Key, Value> updateData) throws StorageException {
    myLock.writeLock().lock();
    try {
      IndexId<?, ?> oldIndexId = IndexDebugProperties.DEBUG_INDEX_ID.get();
      try {
        IndexDebugProperties.DEBUG_INDEX_ID.set(myIndexId);
        boolean hasDifference = updateData.iterateKeys(myAddedKeyProcessor, myUpdatedKeyProcessor, myRemovedKeyProcessor);
        if (hasDifference) updateData.updateForwardIndex();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) { // e.g. IOException, AssertionError
        throw new StorageException(e);
      }
      finally {
        IndexDebugProperties.DEBUG_INDEX_ID.set(oldIndexId);
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public static <Key, Value> void checkValuesHaveProperEqualsAndHashCode(@NotNull Map<Key, Value> data,
                                                                         @NotNull IndexId<Key, Value> indexId,
                                                                         @NotNull DataExternalizer<Value> valueExternalizer) {
    if (IndexDebugProperties.DEBUG) {
      for (Map.Entry<Key, Value> e : data.entrySet()) {
        final Value value = e.getValue();
        if (!(Comparing.equal(value, value) && (value == null || value.hashCode() == value.hashCode()))) {
          LOG.error("Index " + indexId + " violates equals / hashCode contract for Value parameter");
        }

        try {
          final BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
          DataOutputStream outputStream = new DataOutputStream(out);
          valueExternalizer.save(outputStream, value);
          outputStream.close();
          final Value deserializedValue =
            valueExternalizer.read(new DataInputStream(out.toInputStream()));

          if (!(Comparing.equal(value, deserializedValue) && (value == null || value.hashCode() == deserializedValue.hashCode()))) {
            LOG.error("Index " + indexId + " deserialization violates equals / hashCode contract for Value parameter");
          }
        }
        catch (IOException ex) {
          LOG.error(ex);
        }
      }
    }
  }
}

