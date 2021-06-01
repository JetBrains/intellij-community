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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.impl.forward.IntForwardIndexAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class MapReduceIndex<Key,Value, Input> implements InvertedIndex<Key, Value, Input> {
  private static final Logger LOG = Logger.getInstance(MapReduceIndex.class);
  @NotNull protected final IndexId<Key, Value> myIndexId;
  @NotNull protected final IndexStorage<Key, Value> myStorage;

  protected final AtomicLong myModificationStamp = new AtomicLong();
  protected final DataIndexer<Key, Value, Input> myIndexer;
  private final @Nullable ValueSerializationChecker<Value, Input> myValueSerializationChecker;
  private final @NotNull IndexExtension<Key, Value, Input> myExtension;

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
                           @NotNull IndexStorageLayout<Key, Value> indexStorageLayout,
                           @Nullable ReadWriteLock lock) throws IOException {
    this(extension,
         indexStorageLayout.openIndexStorage(),
         indexStorageLayout.openForwardIndex(),
         indexStorageLayout.getForwardIndexAccessor(),
         lock);
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                           @Nullable ReadWriteLock lock) throws IOException {
    this(extension, () -> storage, () -> forwardIndex, forwardIndexAccessor, lock);
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storage,
                           @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                           @Nullable ReadWriteLock lock) throws IOException {
    myIndexId = extension.getName();
    myExtension = extension;
    myIndexer = myExtension.getIndexer();
    myStorage = storage.compute();
    try {
      myForwardIndex = forwardIndex == null ? null : forwardIndex.compute();
    } catch (IOException e) {
      clearAndDispose();
      throw e;
    }
    myForwardIndexAccessor = forwardIndexAccessor;
    myUseIntForwardIndex = myForwardIndex instanceof IntForwardIndex && myForwardIndexAccessor instanceof IntForwardIndexAccessor;
    LOG.assertTrue(myForwardIndex instanceof IntForwardIndex == myForwardIndexAccessor instanceof IntForwardIndexAccessor,
                   "Invalid index configuration for " + myIndexId);
    myLock = lock == null ? new ReentrantReadWriteLock() : lock;
    myValueSerializationChecker = IndexDebugProperties.DEBUG ? new ValueSerializationChecker<>(extension) : null;
  }

  protected void clearAndDispose() {
    try {
      clear();
      dispose();
    } catch (Exception e) {
      LOG.info(e);
    }
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
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
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable Input content)
    throws MapReduceIndexMappingException, ProcessCanceledException {
    InputData<Key, Value> data;
    try {
      data = mapInput(inputId, content);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (MapReduceIndexMappingException e) {
      throw e;
    }
    catch (Exception e) {
      throw new MapReduceIndexMappingException(e, myExtension.getClass());
    }

    UpdateData<Key, Value> updateData = new UpdateData<>(
      inputId,
      data.getKeyValues(),
      () -> getKeysDiffBuilder(inputId),
      myIndexId,
      () -> updateForwardIndex(inputId, data)
    );

    return new IndexUpdateComputable(updateData, data);
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
    if (myValueSerializationChecker != null) {
      myValueSerializationChecker.checkValuesHaveProperEqualsAndHashCode(data, content);
    }
    checkCanceled();
    return new InputData<>(data);
  }

  @NotNull
  protected Map<Key, Value> mapByIndexer(int inputId, @NotNull Input content) {
    return myIndexer.map(content);
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
      myStorage.updateValue(key, inputId, value);
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
        LOG.error("ProcessCanceledException is not expected here!", e);
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

  @ApiStatus.Internal
  public class IndexUpdateComputable implements Computable<Boolean> {
    @NotNull
    private final UpdateData<Key, Value> myUpdateData;
    @NotNull
    private final InputData<Key, Value> myInputData;

    private IndexUpdateComputable(@NotNull UpdateData<Key, Value> updateData, @NotNull InputData<Key, Value> inputData) {
      myUpdateData = updateData;
      myInputData = inputData;
    }

    @NotNull
    public InputData<Key, Value> getInputData() {
      return myInputData;
    }

    @Override
    public Boolean compute() {
      try {
        MapReduceIndex.this.updateWithMap(myUpdateData);
      }
      catch (StorageException | ProcessCanceledException ex) {
        String message = "An exception during updateWithMap(). Index " + myIndexId.getName() + " will be rebuilt.";
        //noinspection InstanceofCatchParameter
        if (ex instanceof ProcessCanceledException) {
          LOG.error(message, ex);
        }
        else {
          if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
            LOG.error(message, ex);
          }
          else {
            LOG.info(message, ex);
          }
        }
        MapReduceIndex.this.requestRebuild(ex);
        return false;
      }
      return true;
    }
  }
}

