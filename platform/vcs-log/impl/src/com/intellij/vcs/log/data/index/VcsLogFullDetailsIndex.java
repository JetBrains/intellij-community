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
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import static com.intellij.vcs.log.data.index.VcsLogPersistentIndex.getVersion;

public class VcsLogFullDetailsIndex<T> implements Disposable {
  protected static final String INDEX = "index";
  @NotNull protected final MyMapReduceIndex myMapReduceIndex;
  @NotNull private final ID<Integer, T> myID;
  @NotNull private final String myLogId;
  @NotNull private final String myName;
  @NotNull protected final DataIndexer<Integer, T, VcsFullCommitDetails> myIndexer;
  @NotNull private final FatalErrorHandler myFatalErrorHandler;

  public VcsLogFullDetailsIndex(@NotNull String logId,
                                @NotNull String name,
                                final int version,
                                @NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                                @NotNull DataExternalizer<T> externalizer,
                                @NotNull FatalErrorHandler fatalErrorHandler,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myID = ID.create(name);
    myName = name;
    myLogId = logId;
    myIndexer = indexer;
    myFatalErrorHandler = fatalErrorHandler;

    myMapReduceIndex = new MyMapReduceIndex(myIndexer, externalizer, version);

    Disposer.register(disposableParent, this);
  }

  @NotNull
  public TIntHashSet getCommitsWithAnyKey(@NotNull Set<Integer> keys) throws StorageException {
    TIntHashSet result = new TIntHashSet();

    for (Integer key : keys) {
      iterateCommitIds(key, result::add);
    }

    return result;
  }

  @NotNull
  public ValueContainer.IntIterator getCommitsWithAllKeys(@NotNull Collection<Integer> keys) throws StorageException {
    return FileBasedIndexImpl.collectInputIdsContainingAllKeys(myMapReduceIndex, keys);
  }

  private void iterateCommitIds(int key, @NotNull Consumer<Integer> consumer) throws StorageException {
    ValueContainer<T> data = myMapReduceIndex.getData(key);

    ValueContainer.ValueIterator<T> valueIt = data.getValueIterator();
    while (valueIt.hasNext()) {
      valueIt.next();
      ValueContainer.IntIterator inputIt = valueIt.getInputIdsIterator();
      while (inputIt.hasNext()) {
        consumer.consume(inputIt.next());
      }
    }
  }

  protected void iterateCommitIdsAndValues(int key, @NotNull ObjIntConsumer<T> consumer) throws StorageException {
    ValueContainer<T> data = myMapReduceIndex.getData(key);

    ValueContainer.ValueIterator<T> valueIt = data.getValueIterator();
    while (valueIt.hasNext()) {
      T nextValue = valueIt.next();
      ValueContainer.IntIterator inputIt = valueIt.getInputIdsIterator();
      while (inputIt.hasNext()) {
        int next = inputIt.next();
        consumer.accept(nextValue, next);
      }
    }
  }

  public void update(int commitId, @NotNull VcsFullCommitDetails details) throws IOException {
    myMapReduceIndex.update(commitId, details).compute();
  }

  public void flush() throws StorageException {
    myMapReduceIndex.flush();
  }

  @Override
  public void dispose() {
    myMapReduceIndex.dispose();
  }

  @NotNull
  public static File getStorageFile(@NotNull String kind, @NotNull String id) {
    return PersistentUtil.getStorageFile(INDEX, kind, id, getVersion(), false);
  }

  private class MyMapReduceIndex extends MapReduceIndex<Integer, T, VcsFullCommitDetails> {

    public MyMapReduceIndex(@NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                            @NotNull DataExternalizer<T> externalizer,
                            int version) throws IOException {
      super(new MyIndexExtension(indexer, externalizer, version),
            new MapIndexStorage<>(getStorageFile(myName, myLogId),
                                  EnumeratorIntegerDescriptor.INSTANCE,
                                  externalizer, 5000));
    }

    @Override
    protected PersistentHashMap<Integer, Collection<Integer>> createInputsIndex() throws IOException {
      return null;
    }

    @Override
    protected void requestRebuild(@Nullable Exception ex) {
      myFatalErrorHandler.consume(this, ex != null ? ex : new Exception("Index rebuild requested"));
    }
  }

  private class MyIndexExtension extends IndexExtension<Integer, T, VcsFullCommitDetails> {
    @NotNull private final DataIndexer<Integer, T, VcsFullCommitDetails> myIndexer;
    @NotNull private final DataExternalizer<T> myExternalizer;
    private final int myVersion;

    public MyIndexExtension(@NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                            @NotNull DataExternalizer<T> externalizer,
                            int version) {
      myIndexer = indexer;
      myExternalizer = externalizer;
      myVersion = version;
    }

    @NotNull
    @Override
    public ID<Integer, T> getName() {
      return myID;
    }

    @NotNull
    @Override
    public DataIndexer<Integer, T, VcsFullCommitDetails> getIndexer() {
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
