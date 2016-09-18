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
import com.intellij.util.PathUtilRt;
import com.intellij.util.indexing.*;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.ObjIntConsumer;

public class VcsLogFullDetailsIndex<T> implements Disposable {
  @NotNull protected static final String INDEX = "index-";
  @NotNull protected static final String INDEX_INPUTS = "index-inputs-";
  @NotNull protected final MyMapReduceIndex myMapReduceIndex;
  @NotNull private final ID<Integer, T> myID;
  @NotNull private final String myLogId;
  @NotNull private final String myName;
  @NotNull protected final DataIndexer<Integer, T, VcsFullCommitDetails> myIndexer;

  public VcsLogFullDetailsIndex(@NotNull String logId,
                                @NotNull String name,
                                final int version,
                                @NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                                @NotNull DataExternalizer<T> externalizer,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myID = ID.create(name);
    myName = name;
    myLogId = logId;
    myIndexer = indexer;

    MyMapReduceIndex result = IOUtil.openCleanOrResetBroken(() -> new MyMapReduceIndex(myIndexer, externalizer, version),
                                                            () -> {
                                                              IOUtil.deleteAllFilesStartingWith(getStorageFile(version));
                                                              IOUtil.deleteAllFilesStartingWith(getInputsStorageFile(version));
                                                            });
    if (result == null) throw new IOException("Can not create " + myName + " index for " + myLogId);
    myMapReduceIndex = result;

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

  public boolean isIndexed(int commit) throws IOException {
    return myMapReduceIndex.isIndexed(commit);
  }

  @Override
  public void dispose() {
    myMapReduceIndex.dispose();
  }

  protected void onNotIndexableCommit(int commit) throws StorageException {
  }

  public void markCorrupted() {
    myMapReduceIndex.markCorrupted();
  }

  @NotNull
  private File getStorageFile(int version) {
    return getStorageFile(INDEX + myName, myLogId, version);
  }

  @NotNull
  private File getInputsStorageFile(int version) {
    return PersistentUtil.getStorageFile(INDEX_INPUTS + myName, myLogId, version);
  }

  @NotNull
  public static File getStorageFile(@NotNull String kind, @NotNull String id, int version) {
    File subdir = new File(PersistentUtil.LOG_CACHE, kind);
    String safeLogId = PathUtilRt.suggestFileName(id, true, true);
    return new File(subdir, safeLogId + "." + version);
  }

  @Nullable
  protected Collection<Integer> getKeysForCommit(int commit) throws IOException {
    return myMapReduceIndex.getInputsIndex().get(commit);
  }

  private class MyMapReduceIndex extends MapReduceIndex<Integer, T, VcsFullCommitDetails> {

    public MyMapReduceIndex(@NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                            @NotNull DataExternalizer<T> externalizer,
                            int version) throws IOException {
      super(new MyIndexExtension(indexer, externalizer, version),
            new MapIndexStorage<>(getStorageFile(version),
                                  EnumeratorIntegerDescriptor.INSTANCE,
                                  externalizer, 5000));
    }

    @NotNull
    public PersistentHashMap<Integer, Collection<Integer>> getInputsIndex() {
      return myInputsIndex;
    }

    public boolean isIndexed(int commitId) throws IOException {
      return myInputsIndex.containsMapping(commitId);
    }

    @Override
    protected PersistentHashMap<Integer, Collection<Integer>> createInputsIndex() throws IOException {
      IndexExtension<Integer, T, VcsFullCommitDetails> extension = getExtension();
      return new PersistentHashMap<>(getInputsStorageFile(extension.getVersion()),
                                     EnumeratorIntegerDescriptor.INSTANCE,
                                     new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), myID));
    }

    @Override
    protected void updateWithMap(int inputId, @NotNull UpdateData<Integer, T> updateData) throws StorageException {
      if (((SimpleUpdateData)updateData).getNewData().isEmpty()) {
        onNotIndexableCommit(inputId);
      }
      super.updateWithMap(inputId, updateData);
    }

    public void markCorrupted() {
      myInputsIndex.markCorrupted();
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
