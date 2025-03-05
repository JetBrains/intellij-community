// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.impl.forward.IntForwardIndexAccessor;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.io.MeasurableIndexStore.keysCountApproximatelyIfPossible;

//MAYBE RC: MapReduceIndex vs MapReduceIndexBase is a bit odd naming/semantics, because MapReduceIndexBase is an abstract
//          implementation of UpdatableIndex, but significant part of UpdatableIndex methods are implemented in MapReduceIndex
//          Probably, it is better to rename MapReduceIndexBase -> MapReduceUpdatableIndex?
@Internal
public abstract class MapReduceIndex<Key, Value, Input> implements InvertedIndex<Key, Value, Input>,
                                                                   MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(MapReduceIndex.class);


  private final IndexExtension<Key, Value, Input> myExtension;
  /** = extension.getName(), cached because very frequently accessed */
  private final IndexId<Key, Value> myIndexId;
  /** = extension.getIndexer() */
  private final DataIndexer<Key, Value, Input> myIndexer;

  private final IndexStorage<Key, Value> myStorage;
  private final @Nullable ForwardIndex myForwardIndex;
  private final @Nullable ForwardIndexAccessor<Key, Value> myForwardIndexAccessor;
  private final boolean myUseIntForwardIndex;

  private final AtomicLong myModificationStamp = new AtomicLong();


  /**
   * Checks Value objects follow normal equals/hashCode contract: i.e. equals/hashCode are stable, and value after
   * serialization-deserialization is equal to the original value before serialization.
   * <p>
   * The check is expensive, so it is really executed only if {@link IndexDebugProperties#DEBUG}, and not in stress-tests
   *
   * @see ValueSerializationChecker#checkValueSerialization(Map, Object) for details
   */
  private final @Nullable ValueSerializationChecker<Value, Input> myValueSerializationChecker;


  private volatile boolean myDisposed;


  private final LowMemoryWatcher myLowMemoryFlusher;


  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorageLayout<Key, Value> indexStorageLayout) throws IOException {
    this(extension,
         indexStorageLayout.openIndexStorage(),
         indexStorageLayout.openForwardIndex(),
         indexStorageLayout.getForwardIndexAccessor());
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex forwardIndex,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
    this(extension, () -> storage, () -> forwardIndex, forwardIndexAccessor);
  }

  protected MapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storageFactory,
                           @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndexFactory,
                           @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
    myIndexId = extension.getName();
    myExtension = extension;
    myIndexer = myExtension.getIndexer();
    myStorage = storageFactory.compute();
    try {
      myForwardIndex = forwardIndexFactory == null ? null : forwardIndexFactory.compute();
    }
    catch (IOException e) {
      tryDispose();
      throw e;
    }
    myForwardIndexAccessor = forwardIndexAccessor;
    myUseIntForwardIndex = myForwardIndex instanceof IntForwardIndex && myForwardIndexAccessor instanceof IntForwardIndexAccessor;
    LOG.assertTrue(myForwardIndex instanceof IntForwardIndex == myForwardIndexAccessor instanceof IntForwardIndexAccessor,
                   "Invalid index configuration for " + myIndexId);
    myValueSerializationChecker = new ValueSerializationChecker<>(extension, getSerializationProblemReporter());
    myLowMemoryFlusher = LowMemoryWatcher.register(() -> clearCaches());
  }


  public @NotNull IndexExtension<Key, Value, Input> getExtension() {
    return myExtension;
  }

  /** =extension.getName() */
  public IndexId<Key, Value> indexId() {
    return myIndexId;
  }

  /** = extension.getIndexer() */
  protected DataIndexer<Key, Value, Input> indexer() {
    return myIndexer;
  }

  public @NotNull IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  protected @NotNull ValueSerializationProblemReporter getSerializationProblemReporter() {
    return ValueSerializationChecker.DEFAULT_SERIALIZATION_PROBLEM_REPORTER;
  }

  public @Nullable ForwardIndex getForwardIndex() {
    return myForwardIndex;
  }

  public @Nullable ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() {
    return myForwardIndexAccessor;
  }

  public long getModificationStamp() {
    return myModificationStamp.get();
  }


  public void clearCaches() {
    try {
      //TODO RC: it seems useless to clearCaches() before flush() -- clearCaches() basically trims
      //         mergedSnapshot from all the cached ChangeTrackingValueContainers, while flush() in
      //         its current implementation persists all the changes in those containers, AND
      //         invalidates the cache entirely, i.e. remove all the cached content. So flush()
      //         strongly tops .clearCaches() in its effect on occupied heap space.
      myStorage.clearCaches();
      flush();
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
  }

  protected void tryDispose() {
    try {
      dispose();
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  @Override
  public void clear() {
    //TODO RC: do we need a lock around here?
    try {
      incrementModificationStamp();
      doClear();
    }
    catch (StorageException | IOException e) {
      LOG.info(e);
    }
  }

  //@GuardedWith(myStorage.writeLock)
  protected void doClear() throws StorageException, IOException {
    myStorage.clear();
    if (myForwardIndex != null) myForwardIndex.clear();
  }

  @Override
  public void flush() throws StorageException {
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
  }

  public boolean isDirty() {
    if (myForwardIndex != null && myForwardIndex.isDirty()) {
      return true;
    }
    return myStorage.isDirty();
  }

  protected void doFlush() throws IOException, StorageException {
    //A subtle thing about flush operation in general is that it is _logically_ a read-op, just a copy of in-memory
    // state onto a disk -- so it looks quite natural to protect it with readLock only. It is also very _desirable_
    // to go with readLock, since flush involves an IO, and holding an exclusive writeLock while doing potentially
    // long IO is undesirable from app responsiveness PoV.
    //But technically, implementation-wise, a flush is almost never a pure read-op -- it almost always involves
    // some write-operations, so readLock is almost never enough.
    //Here the use of readLock is _partially_ justified by a careful (and definitely not obvious) design of
    // ChangeTrackingValueContainer which is not even mentioned anywhere in MapReduceIndex: CTVContainer is lazy,
    // but it is designed in such a way that it could save either its fully-loaded state _or_ diff-state in any
    // moment, and both representations will be correct. All this is very tricky and subtle.
    //And also it seems only partially correct, because it is not clear are all the accesses properly synchronized
    // (ordered) from concurrency PoV -- I really believe they are not properly synchronized, but can't point specific
    // violation now, and also don't have a solution at hand for how to fix it.
    //TODO RC: do we need a lock around here?
    if (myForwardIndex != null) {
      myForwardIndex.force();
    }
    myStorage.flush();
  }

  @Override
  public void dispose() {
    myLowMemoryFlusher.stop();
    //TODO RC: do we need a lock around here?
    try {
      myDisposed = true;
      doDispose();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
  }

  @Override
  public int keysCountApproximately() {
    return keysCountApproximatelyIfPossible(myStorage);
  }

  protected boolean isDisposed() {
    return myDisposed;
  }

  protected void doDispose() throws StorageException {
    try {
      myStorage.close();
    }
    catch (IOException e) {
      throw new StorageException(e);
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
  public <E extends Exception> boolean withData(@NotNull Key key,
                                                @NotNull ValueContainerProcessor<Value, E> processor) throws StorageException, E {
    return myStorage.read(key, processor);
  }

  @Override
  public @NotNull StorageUpdate mapInputAndPrepareUpdate(int inputId, @Nullable Input content)
    throws MapReduceIndexMappingException, ProcessCanceledException {
    InputData<Key, Value> data;
    try {
      data = mapInput(inputId, content);
    }
    catch (ProcessCanceledException | MapReduceIndexMappingException e) {
      throw e;
    }
    catch (Exception e) {
      throw new MapReduceIndexMappingException(e, myExtension.getClass());
    }

    return prepareUpdate(inputId, data);
  }

  @Override
  public @NotNull StorageUpdate prepareUpdate(int inputId, @NotNull InputData<Key, Value> inputData) {
    UpdateData<Key, Value> updateData = new UpdateData<>(
      inputId,
      indexId(),

      changedEntriesProcessor -> {
        try {
          InputDataDiffBuilder<Key, Value> diffBuilder = getKeysDiffBuilder(inputId);
          Map<Key, Value> newData = inputData.getKeyValues();
          return diffBuilder.differentiate(newData, changedEntriesProcessor);
        }
        catch (IOException e) {
          throw new StorageException("Error while applying " + this, e);
        }
      },

      () -> updateForwardIndex(inputId, inputData)
    );

    return new IndexStorageUpdate(inputData, updateData);
  }

  @ApiStatus.Internal
  protected void checkNonCancellableSection() { }

  protected void updateForwardIndex(int inputId, @NotNull InputData<Key, Value> data) throws IOException {
    if (myForwardIndex == null) {
      return;
    }

    if (myUseIntForwardIndex) {
      IntForwardIndex forwardIndex = (IntForwardIndex)myForwardIndex;
      IntForwardIndexAccessor<Key, Value> forwardIndexAccessor = (IntForwardIndexAccessor<Key, Value>)myForwardIndexAccessor;

      int value = forwardIndexAccessor.serializeIndexedDataToInt(data);
      forwardIndex.putInt(inputId, value);
      return;
    }

    myForwardIndex.put(inputId, myForwardIndexAccessor.serializeIndexedData(data));
  }

  protected @NotNull InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (myForwardIndex == null) {
      return new EmptyInputDataDiffBuilder<>(inputId);
    }

    if (myUseIntForwardIndex) {
      IntForwardIndex forwardIndex = (IntForwardIndex)myForwardIndex;
      IntForwardIndexAccessor<Key, Value> accessor = (IntForwardIndexAccessor<Key, Value>)myForwardIndexAccessor;

      int value = forwardIndex.getInt(inputId);
      return accessor.getDiffBuilderFromInt(inputId, value);
    }

    return myForwardIndexAccessor.getDiffBuilder(inputId, myForwardIndex.get(inputId));
  }

  protected @NotNull InputData<Key, Value> mapInput(int inputId, @Nullable Input content) {
    if (content == null) {
      return InputData.empty();
    }
    Map<Key, Value> data = mapByIndexer(inputId, content);
    if (myValueSerializationChecker != null) {
      myValueSerializationChecker.checkValueSerialization(data, content);
    }
    checkCanceled();
    return new InputData<>(data);
  }

  protected @NotNull Map<Key, Value> mapByIndexer(int inputId, @NotNull Input content) {
    return myIndexer.map(content);
  }

  public abstract void checkCanceled();

  protected abstract void requestRebuild(@NotNull Throwable e);

  private final UpdatedEntryProcessor<Key, Value> changedEntriesProcessor = new UpdatedEntryProcessor<Key, Value>() {
    @Override
    public void process(@NotNull UpdateKind kind, Key key, Value value, int inputId) throws StorageException {
      incrementModificationStamp();
      switch (kind) {
        case ADDED:
          myStorage.addValue(key, inputId, value);
          break;
        case UPDATED:
          myStorage.updateValue(key, inputId, value);
          break;
        case REMOVED:
          myStorage.removeAllValues(key, inputId);
          break;
      }
    }
  };

  protected void incrementModificationStamp() {
    myModificationStamp.incrementAndGet();
  }

  public void updateWith(@NotNull UpdateData<Key, Value> updateData) throws StorageException {
    IndexId<?, ?> oldIndexId = IndexDebugProperties.DEBUG_INDEX_ID.get();
    try {
      IndexDebugProperties.DEBUG_INDEX_ID.set(myIndexId);

      //Notes about forward/inverted storage data consistency: ideally, both forward and inverted index storages should
      // be updated under the same (write)lock -- to prevent their content becoming out-of-sync. This is undesirable since
      // to implement that we need to _expose_ the lock encapsulated in a forward/inverted storage to use it to protect both
      // storages.
      // We solve that issue here by relying on the pipelining nature of index update: forward index data is used _only_ to
      // calculate an old/new state diff during an inverted index update -- i.e. down the .iterateChanges() callstack below.
      // So if all the updates on a single index are applied by a single thread -- this ensures the current update won't
      // start until the thread finishes previous update, i.e. until both inverted and forward index are updated with previous
      // updateData. Hence, current update on the same thread always sees forward/inverted storage state being consistent,
      // because the previous update was already completely applied -- even though during the previous update application
      // there _was_ a timeframe when they are/were not consistent, but that intermediate state is (it seems to me) unobservable
      // if all the updates are rolled on from the same thread.

      boolean hasDifference = updateData.iterateChanges(changedEntriesProcessor);

      if (hasDifference) {
        updateData.updateForwardIndex();
      }
    }
    catch (CancellationException e) {
      LOG.error("CancellationException is not expected here! (" + e + ")");
      throw e;
    }
    catch (Throwable e) { // e.g. IOException, AssertionError
      throw new StorageException(e);
    }
    finally {
      IndexDebugProperties.DEBUG_INDEX_ID.set(oldIndexId);
    }
  }

  @Internal
  public final class IndexStorageUpdate implements StorageUpdate {
    //RC: inputData is excessive here: it is used just to access inputMap, but that same map is also available inside UpdateData,
    //    just there is no accessor to it
    private final InputData<Key, Value> inputData;
    private final UpdateData<Key, Value> updateData;

    private IndexStorageUpdate(@NotNull InputData<Key, Value> inputData,
                               @NotNull UpdateData<Key, Value> updateData) {
      this.inputData = inputData;
      this.updateData = updateData;
    }

    public @NotNull InputData<Key, Value> getInputData() {
      return inputData;
    }

    @Override
    public boolean update() {
      checkNonCancellableSection();
      try {
        MapReduceIndex.this.updateWith(updateData);
      }
      catch (StorageException | CancellationException ex) {
        logStorageUpdateException(ex);

        MapReduceIndex.this.requestRebuild(ex);
        return false;
      }
      catch (Throwable t) {
        LOG.error("An exception during updateWithMap(). Index " + myIndexId.getName(), t);
        throw t;
      }
      return true;
    }

    private void logStorageUpdateException(@NotNull Exception ex) {
      String message = "An exception during updateWithMap(). Index " + myIndexId.getName() + " will be rebuilt.";
      if (ex instanceof CancellationException) {
        //It an error to log a (P)CE (see Logger.ensureNotControlFlow)
        LOG.error(message + " (CancellationException: " + ex + ")");
      }
      else {
        if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
          LOG.error(message, ex);
        }
        else {
          LOG.info(message, ex);
        }
      }
    }

    @Override
    public String toString() {
      return "IndexUpdate[" + myIndexId + "][fileId: " + updateData.inputId() + "]{" + inputData.getKeyValues().size() + " values}";
    }
  }
}
