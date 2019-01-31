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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApiStatus.Experimental
public abstract class MapReduceIndex<Key,Value, Input> implements InvertedIndex<Key, Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.impl.MapReduceIndex");
  @NotNull protected final IndexId<Key, Value> myIndexId;
  @NotNull protected final IndexStorage<Key, Value> myStorage;

  protected final DataExternalizer<Value> myValueExternalizer;
  protected final IndexExtension<Key, Value, Input> myExtension;
  private final AtomicLong myModificationStamp = new AtomicLong();
  private final DataIndexer<Key, Value, Input> myIndexer;

  protected final ForwardIndex<Key, Value> myForwardIndex;

  private final ReentrantReadWriteLock myLock = createLock();
  private volatile boolean myDisposed;

  private final LowMemoryWatcher myLowMemoryFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      try {
        getReadLock().lock();
        try {
          myStorage.clearCaches();
        }
        finally {
          getReadLock().unlock();
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
                           @Nullable ForwardIndex<Key, Value> forwardIndex) {
    myIndexId = extension.getName();
    myExtension = extension;
    myIndexer = myExtension.getIndexer();
    myStorage = storage;
    myValueExternalizer = extension.getValueExternalizer();
    myForwardIndex = forwardIndex;
  }

  @NotNull
  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  @NotNull
  protected ReentrantReadWriteLock createLock() {
    return new ReentrantReadWriteLock();
  }

  @NotNull
  public final ReentrantReadWriteLock getLock() {
    return myLock;
  }

  @Override
  public void clear() {
    try {
      getWriteLock().lock();
      doClear();
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

  protected void doClear() throws StorageException, IOException {
    myStorage.clear();
    if (myForwardIndex != null) myForwardIndex.clear();
  }

  @Override
  public void flush() throws StorageException{
    try {
      getReadLock().lock();
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
      getReadLock().unlock();
    }
  }

  protected void doFlush() throws IOException, StorageException {
    if (myForwardIndex != null) myForwardIndex.flush();
    myStorage.flush();
  }

  @Override
  public void dispose() {
    myLowMemoryFlusher.stop();
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      doDispose();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      myDisposed = true;
      lock.unlock();
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

  @NotNull
  public final Lock getReadLock() {
    return myLock.readLock();
  }

  @NotNull
  public final Lock getWriteLock() {
    return myLock.writeLock();
  }

  @Override
  @NotNull
  public ValueContainer<Value> getData(@NotNull final Key key) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      if (myDisposed) {
        return new ValueContainerImpl<Value>();
      }
      ValueContainerImpl.ourDebugIndexInfo.set(myIndexId);
      return myStorage.read(key);
    }
    finally {
      ValueContainerImpl.ourDebugIndexInfo.set(null);
      lock.unlock();
    }
  }

  @NotNull
  @Override
  public final Computable<Boolean> update(final int inputId, @Nullable final Input content) {
    final UpdateData<Key, Value> updateData = calculateUpdateData(inputId, content);

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
        catch (ProcessCanceledException ex) {
          LOG.info("Exception during updateWithMap:" + ex);
          requestRebuild(ex);
          return Boolean.FALSE;
        }

        return Boolean.TRUE;
      }
    };
  }

  @NotNull
  protected UpdateData<Key, Value> calculateUpdateData(final int inputId, @Nullable Input content) {
    final Map<Key, Value> data = mapInput(content);
    return createUpdateData(data, new ThrowableComputable<InputDataDiffBuilder<Key, Value>, IOException>() {
      @Override
      public InputDataDiffBuilder<Key, Value> compute() throws IOException {
        return getKeysDiffBuilder(inputId);
      }
    }, new ThrowableRunnable<IOException>() {
      @Override
      public void run() throws IOException {
        if (myForwardIndex != null) myForwardIndex.putInputData(inputId, data);
      }
    });
  }

  @NotNull
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (myForwardIndex != null) {
      return myForwardIndex.getDiffBuilder(inputId);
    }
    return new EmptyInputDataDiffBuilder<Key, Value>(inputId);
  }

  @NotNull
  protected UpdateData<Key, Value> createUpdateData(@NotNull Map<Key, Value> data,
                                                    @NotNull ThrowableComputable<InputDataDiffBuilder<Key, Value>, IOException> keys,
                                                    @NotNull ThrowableRunnable<IOException> forwardIndexUpdate) {
    return new UpdateData<Key, Value>(data, keys, myIndexId, forwardIndexUpdate);
  }

  @NotNull
  protected Map<Key, Value> mapInput(@Nullable Input content) {
    if (content == null) {
      return Collections.emptyMap();
    }
    Map<Key, Value> data = myIndexer.map(content);
    checkValuesHaveProperEqualsAndHashCode(data, myIndexId, myValueExternalizer);
    checkCanceled();
    return data;
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

  protected void updateWithMap(final int inputId,
                               @NotNull UpdateData<Key, Value> updateData) throws StorageException {
    getWriteLock().lock();
    try {
      try {
        ValueContainerImpl.ourDebugIndexInfo.set(myIndexId);
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
        ValueContainerImpl.ourDebugIndexInfo.set(null);
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

  public void removeTransientDataForFile(int inputId) {}

  public static <Key, Value> void checkValuesHaveProperEqualsAndHashCode(@NotNull Map<Key, Value> data,
                                                                         @NotNull IndexId<Key, Value> indexId,
                                                                         @NotNull DataExternalizer<Value> valueExternalizer) {
    if (DebugAssertions.DEBUG) {
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

