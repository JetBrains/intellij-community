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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;

public class VcsLogFullDetailsIndex<T, D extends VcsFullCommitDetails> implements Disposable {
  protected static final String INDEX = "index";
  @NotNull protected final MyMapReduceIndex myMapReduceIndex;
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
    ForwardIndex<Integer, T> forwardIndex = createForwardIndex(extension);
    return new MyMapReduceIndex(extension, new MyMapIndexStorage<>(myName, myStorageId, dataExternalizer), forwardIndex);
  }

  @NotNull
  protected ForwardIndex<Integer, T> createForwardIndex(@NotNull IndexExtension<Integer, T, D> extension)
    throws IOException {
    return new EmptyForwardIndex<>();
  }

  @NotNull
  public TIntHashSet getCommitsWithAnyKey(@NotNull Set<Integer> keys) throws StorageException {
    checkDisposed();
    TIntHashSet result = new TIntHashSet();

    for (Integer key : keys) {
      iterateCommitIds(key, result::add);
    }

    return result;
  }

  @NotNull
  public TIntHashSet getCommitsWithAllKeys(@NotNull Collection<Integer> keys) throws StorageException {
    checkDisposed();
    return InvertedIndexUtil.collectInputIdsContainingAllKeys(myMapReduceIndex, keys, (k) -> {
      ProgressManager.checkCanceled();
      return true;
    }, null, null);
  }

  private void iterateCommitIds(int key, @NotNull Consumer<Integer> consumer) throws StorageException {
    ValueContainer<T> data = myMapReduceIndex.getData(key);
    data.forEach((id, value) -> {
      consumer.consume(id);
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
  protected <MapIndexType> MapIndexType getKeysForCommit(int commit) throws IOException {
    MapBasedForwardIndex<Integer, T, MapIndexType> index = myMapReduceIndex.getForwardIndex();
    if (index == null) return null;

    return index.getInput(commit);
  }

  public void update(int commitId, @NotNull D details) {
    checkDisposed();
    myMapReduceIndex.update(commitId, details).compute();
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

  private class MyMapReduceIndex extends MapReduceIndex<Integer, T, D> {
    MyMapReduceIndex(@NotNull MyIndexExtension<T, D> extension,
                     @NotNull MyMapIndexStorage<T> mapIndexStorage,
                     @NotNull ForwardIndex<Integer, T> forwardIndex) {
      super(extension, mapIndexStorage, forwardIndex);
    }

    @Nullable
    public <MapIndexType> MapBasedForwardIndex<Integer, T, MapIndexType> getForwardIndex() {
      if (myForwardIndex instanceof MapBasedForwardIndex) {
        return ((MapBasedForwardIndex<Integer, T, MapIndexType>)myForwardIndex);
      }
      return null;
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

  private static class EmptyForwardIndex<T> implements ForwardIndex<Integer, T> {
    @NotNull
    @Override
    public InputDataDiffBuilder<Integer, T> getDiffBuilder(int inputId) {
      return new EmptyInputDataDiffBuilder<>(inputId);
    }

    @Override
    public void putInputData(int inputId, @NotNull Map<Integer, T> data) {
    }

    @Override
    public void flush() {
    }

    @Override
    public void clear() {
    }

    @Override
    public void close() {
    }
  }
}
