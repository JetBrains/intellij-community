// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

public class VcsLogFullDetailsIndex<T, D> implements Disposable {
  @NonNls protected static final String INDEX = "index";
  @NotNull private final MyMapReduceIndex myMapReduceIndex;
  @NotNull protected final StorageId myStorageId;
  @NotNull protected final String myName;
  @NotNull protected final DataIndexer<Integer, T, D> myIndexer;
  @NotNull private final FatalErrorHandler myFatalErrorHandler;
  private volatile boolean myDisposed = false;

  public VcsLogFullDetailsIndex(@NotNull StorageId storageId,
                                @NotNull String name,
                                @NotNull DataIndexer<Integer, T, D> indexer,
                                @NotNull DataExternalizer<T> externalizer,
                                @NotNull FatalErrorHandler fatalErrorHandler,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myName = name;
    myStorageId = storageId;
    myIndexer = indexer;
    myFatalErrorHandler = fatalErrorHandler;

    myMapReduceIndex = createMapReduceIndex(externalizer);

    Disposer.register(disposableParent, this);
  }

  @NotNull
  private MyMapReduceIndex createMapReduceIndex(@NotNull DataExternalizer<T> dataExternalizer) throws IOException {
    MyIndexExtension<T, D> extension = new MyIndexExtension<>(myName, myIndexer, dataExternalizer, myStorageId.getVersion());
    Pair<ForwardIndex, ForwardIndexAccessor<Integer, T>> pair = createdForwardIndex();
    ForwardIndex forwardIndex = pair != null ? pair.getFirst() : null;
    ForwardIndexAccessor<Integer, T> forwardIndexAccessor = pair != null ? pair.getSecond() : null;
    return new MyMapReduceIndex(extension, new MyMapIndexStorage<>(myName, myStorageId, dataExternalizer), forwardIndex,
                                forwardIndexAccessor);
  }

  @Nullable
  protected Pair<ForwardIndex, ForwardIndexAccessor<Integer, T>> createdForwardIndex() throws IOException {
    return null;
  }

  @NotNull
  public IntSet getCommitsWithAnyKey(@NotNull IntSet keys) throws StorageException {
    checkDisposed();
    IntSet result = new IntOpenHashSet();
    for (IntIterator iterator = keys.iterator(); iterator.hasNext(); ) {
      int key = iterator.nextInt();
      iterateCommitIds(key, result::add);
    }

    return result;
  }

  @NotNull
  public IntSet getCommitsWithAllKeys(@NotNull Collection<Integer> keys) throws StorageException {
    checkDisposed();
    return InvertedIndexUtil.collectInputIdsContainingAllKeys(myMapReduceIndex, keys, (k) -> {
      ProgressManager.checkCanceled();
      return true;
    }, null, null);
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

  @Nullable
  protected Collection<Integer> getKeysForCommit(int commit) throws IOException {
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

  public void flush() throws StorageException {
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
      myFatalErrorHandler.consume(this, ex);
    }
  }

  private static class MyMapIndexStorage<T> extends MapIndexStorage<Integer, T> {
    MyMapIndexStorage(@NotNull String name, @NotNull StorageId storageId, @NotNull DataExternalizer<T> externalizer)
      throws IOException {
      super(storageId.getStorageFile(name, true), EnumeratorIntegerDescriptor.INSTANCE, externalizer, 5000, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }
  }

  private static class MyIndexExtension<T, D> extends IndexExtension<Integer, T, D> {
    @NotNull private final IndexId<Integer, T> myID;
    @NotNull private final DataIndexer<Integer, T, D> myIndexer;
    @NotNull private final DataExternalizer<T> myExternalizer;
    private final int myVersion;

    MyIndexExtension(@NotNull String name, @NotNull DataIndexer<Integer, T, D> indexer,
                     @NotNull DataExternalizer<T> externalizer,
                     int version) {
      myID = IndexId.create(name);
      myIndexer = indexer;
      myExternalizer = externalizer;
      myVersion = version;
    }

    @NotNull
    @Override
    public IndexId<Integer, T> getName() {
      return myID;
    }

    @NotNull
    @Override
    public DataIndexer<Integer, T, D> getIndexer() {
      return myIndexer;
    }

    @NotNull
    @Override
    public KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<T> getValueExternalizer() {
      return myExternalizer;
    }

    @Override
    public int getVersion() {
      return myVersion;
    }
  }
}
