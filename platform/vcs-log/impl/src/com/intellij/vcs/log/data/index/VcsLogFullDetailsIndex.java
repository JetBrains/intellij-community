// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.io.*;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

public class VcsLogFullDetailsIndex<T, D> implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogFullDetailsIndex.class);
  private final @NotNull MyMapReduceIndex myMapReduceIndex;
  protected final @NotNull StorageId.Directory myStorageId;
  protected final @NotNull String myName;
  protected final @NotNull DataIndexer<Integer, T, D> myIndexer;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private volatile boolean myDisposed = false;

  public VcsLogFullDetailsIndex(@NotNull StorageId.Directory storageId,
                                @NotNull String name,
                                @NotNull DataIndexer<Integer, T, D> indexer,
                                @NotNull DataExternalizer<T> externalizer,
                                @Nullable StorageLockContext storageLockContext,
                                @NotNull VcsLogErrorHandler errorHandler,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myName = name;
    myStorageId = storageId;
    myIndexer = indexer;
    myErrorHandler = errorHandler;

    myMapReduceIndex = createMapReduceIndex(externalizer, storageLockContext);

    Disposer.register(disposableParent, this);
  }

  private @NotNull MyMapReduceIndex createMapReduceIndex(@NotNull DataExternalizer<T> dataExternalizer,
                                                         @Nullable StorageLockContext storageLockContext) throws IOException {
    MyIndexExtension<T, D> extension = new MyIndexExtension<>(myName, myIndexer, dataExternalizer, myStorageId.getVersion());
    Pair<ForwardIndex, ForwardIndexAccessor<Integer, T>> pair = createdForwardIndex(storageLockContext);
    ForwardIndex forwardIndex = pair != null ? pair.getFirst() : null;
    ForwardIndexAccessor<Integer, T> forwardIndexAccessor = pair != null ? pair.getSecond() : null;
    PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(storageLockContext);
    try {
      return new MyMapReduceIndex(extension, new MyMapIndexStorage<>(myName, myStorageId, dataExternalizer), forwardIndex,
                                  forwardIndexAccessor);
    }
    finally {
      PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.remove();
    }
  }

  protected @Nullable Pair<ForwardIndex, ForwardIndexAccessor<Integer, T>> createdForwardIndex(@Nullable StorageLockContext storageLockContext) throws IOException {
    return null;
  }

  public boolean isEmpty() throws IOException {
    return ((MyMapIndexStorage<T>)myMapReduceIndex.getStorage()).isEmpty();
  }

  public @NotNull IntSet getCommitsWithAnyKey(@NotNull IntSet keys) throws StorageException {
    checkDisposed();
    IntSet result = new IntOpenHashSet();
    for (IntIterator iterator = keys.iterator(); iterator.hasNext(); ) {
      int key = iterator.nextInt();
      iterateCommitIds(key, result::add);
    }

    return result;
  }

  public @NotNull IntSet getCommitsWithAllKeys(@NotNull Collection<Integer> keys) throws StorageException {
    checkDisposed();
    return InvertedIndexUtil.collectInputIdsContainingAllKeys(myMapReduceIndex,
                                                              keys,
                                                              null,
                                                              null);
  }

  private void iterateCommitIds(int key, @NotNull IntConsumer consumer) throws StorageException {
    ValueContainer<T> data = myMapReduceIndex.getData(key);
    data.forEach((id, value) -> {
      consumer.accept(id);
      return true;
    });
  }

  protected void iterateCommitIdsAndValues(int key, @NotNull ObjIntConsumer<? super T> consumer) throws StorageException {
    myMapReduceIndex.getData(key).forEach((id, value) -> {
      consumer.accept(value, id);
      return true;
    });
  }

  protected @Nullable Collection<Integer> getKeysForCommit(int commit) throws IOException {
    ForwardIndex forwardIndex = myMapReduceIndex.getForwardIndex();
    KeyCollectionForwardIndexAccessor<Integer, T> forwardIndexAccessor =
      ((KeyCollectionForwardIndexAccessor<Integer, T>)myMapReduceIndex.getForwardIndexAccessor());
    if (forwardIndex == null || forwardIndexAccessor == null) return null;
    return forwardIndexAccessor.deserializeData(forwardIndex.get(commit));
  }

  public void update(int commitId, @NotNull D details) {
    checkDisposed();
    myMapReduceIndex.mapInputAndPrepareUpdate(commitId, details).compute();
  }

  public void clearCaches() {
    myMapReduceIndex.clearCaches();
  }

  public void flush() throws StorageException, IOException {
    checkDisposed();
    myMapReduceIndex.flush();
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myMapReduceIndex.dispose();
  }

  private void checkDisposed() {
    if (myDisposed) throw new ProcessCanceledException();
  }

  private final class MyMapReduceIndex extends MapReduceIndex<Integer, T, D> {
    private MyMapReduceIndex(@NotNull MyIndexExtension<T, D> extension,
                             @NotNull IndexStorage<Integer, T> storage,
                             @Nullable ForwardIndex forwardIndex,
                             @Nullable ForwardIndexAccessor<Integer, T> forwardIndexAccessor) throws IOException {
      super(extension, storage, forwardIndex, forwardIndexAccessor);
    }

    @Override
    public void checkCanceled() {
      ProgressManager.checkCanceled();
    }

    @Override
    public void requestRebuild(@NotNull Throwable ex) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, ex);
    }
  }

  private static final class MyMapIndexStorage<T> extends MapIndexStorage<Integer, T> {
    private final @NotNull String myName;

    MyMapIndexStorage(@NotNull String name, @NotNull StorageId.Directory storageId, @NotNull DataExternalizer<T> externalizer)
      throws IOException {
      super(storageId.getStorageFile(name, true), EnumeratorIntegerDescriptor.INSTANCE, externalizer, 500, false);
      myName = name;
    }

    boolean isEmpty() throws IOException {
      Ref<Boolean> isEmpty = new Ref<>(true);
      doProcessKeys(key -> {
        isEmpty.set(false);
        return false;
      });
      return isEmpty.get();
    }

    @Override
    public void clear() throws StorageException {
      LOG.warn("Clearing '" + myName + "' map index storage", new RuntimeException());
      super.clear();
    }
  }

  private static final class MyIndexExtension<T, D> extends IndexExtension<Integer, T, D> {
    private final @NotNull IndexId<Integer, T> myID;
    private final @NotNull DataIndexer<Integer, T, D> myIndexer;
    private final @NotNull DataExternalizer<T> myExternalizer;
    private final int myVersion;

    MyIndexExtension(@NotNull String name, @NotNull DataIndexer<Integer, T, D> indexer,
                     @NotNull DataExternalizer<T> externalizer,
                     int version) {
      myID = IndexId.create(name);
      myIndexer = indexer;
      myExternalizer = externalizer;
      myVersion = version;
    }

    @Override
    public @NotNull IndexId<Integer, T> getName() {
      return myID;
    }

    @Override
    public @NotNull DataIndexer<Integer, T, D> getIndexer() {
      return myIndexer;
    }

    @Override
    public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<T> getValueExternalizer() {
      return myExternalizer;
    }

    @Override
    public int getVersion() {
      return myVersion;
    }
  }
}
