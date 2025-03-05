// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ThrowableRunnable;
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

class VcsLogFullDetailsIndex<T, D> implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogFullDetailsIndex.class);
  private final @NotNull MapReduceIndex<Integer, T, D> myMapReduceIndex;
  private volatile boolean myDisposed = false;

  VcsLogFullDetailsIndex(@NotNull MapReduceIndex<Integer, T, D> mapReduceIndex, @NotNull Disposable disposableParent) {
    myMapReduceIndex = mapReduceIndex;
    Disposer.register(disposableParent, this);
  }

  protected static <T, D> @NotNull MapReduceIndex<Integer, T, D> createMapReduceIndex(@NotNull String name,
                                                                                      @NotNull StorageId.Directory storageId,
                                                                                      @NotNull DataIndexer<Integer, T, D> indexer,
                                                                                      @NotNull DataExternalizer<T> externalizer,
                                                                                      @Nullable StorageLockContext storageLockContext,
                                                                                      @Nullable ForwardIndex forwardIndex,
                                                                                      @Nullable ForwardIndexAccessor<Integer, T> forwardIndexAccessor,
                                                                                      @NotNull VcsLogErrorHandler errorHandler)
    throws IOException {
    MyIndexExtension<T, D> extension = new MyIndexExtension<>(name, indexer, externalizer, storageId.getVersion());
    PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(storageLockContext);
    try {
      return new MyMapReduceIndex<>(extension, new MyMapIndexStorage<>(name, storageId, externalizer), forwardIndex,
                                    forwardIndexAccessor, errorHandler);
    }
    finally {
      PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.remove();
    }
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
    myMapReduceIndex.withData(key, container ->
      container.forEach((id, value) -> {
        consumer.accept(id);
        return true;
      })
    );
  }

  protected void iterateCommitIdsAndValues(int key, @NotNull ObjIntConsumer<? super T> consumer) throws StorageException {
    myMapReduceIndex.withData(key, container ->
      container.forEach((id, value) -> {
        consumer.accept(value, id);
        return true;
      })
    );
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
    myMapReduceIndex.mapInputAndPrepareUpdate(commitId, details).update();
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

  protected static void catchAndWarn(@NotNull Logger logger, @NotNull ThrowableRunnable<IOException> runnable) {
    try {
      runnable.run();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      logger.warn(e);
    }
  }

  private static final class MyMapReduceIndex<T, D> extends MapReduceIndex<Integer, T, D> {
    private final @NotNull VcsLogErrorHandler myErrorHandler;

    private MyMapReduceIndex(@NotNull MyIndexExtension<T, D> extension,
                             @NotNull IndexStorage<Integer, T> storage,
                             @Nullable ForwardIndex forwardIndex,
                             @Nullable ForwardIndexAccessor<Integer, T> forwardIndexAccessor,
                             @NotNull VcsLogErrorHandler errorHandler) throws IOException {
      super(extension, storage, forwardIndex, forwardIndexAccessor);
      myErrorHandler = errorHandler;
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
