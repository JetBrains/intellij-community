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
import com.intellij.util.PathUtilRt;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public class VcsLogFullDetailsIndex implements Disposable {
  @NotNull private final MyMapReduceIndex myMapReduceIndex;
  @NotNull private final ID<Integer, Void> myID;
  private final String myLogId;
  private final String myName;

  public VcsLogFullDetailsIndex(@NotNull String logId,
                                @NotNull String name,
                                int version,
                                @NotNull DataIndexer<Integer, Void, VcsFullCommitDetails> indexer,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myID = ID.create(name);
    myName = name;
    myLogId = logId;

    myMapReduceIndex = new MyMapReduceIndex(indexer, version);

    Disposer.register(disposableParent, this);
  }

  @NotNull
  public TIntHashSet getCommitsWithAllKeys(@NotNull Set<Integer> keys) throws StorageException {
    TIntHashSet result = null;

    for (Integer key : keys) {
      TIntHashSet newResult = new TIntHashSet();
      ValueContainer<Void> data = myMapReduceIndex.getData(key);

      ValueContainer.ValueIterator<Void> valueIt = data.getValueIterator();
      while (valueIt.hasNext()) {
        valueIt.next();
        ValueContainer.IntIterator inputIt = valueIt.getInputIdsIterator();
        while (inputIt.hasNext()) {
          int id = inputIt.next();
          if (result == null || result.contains(id)) {
            newResult.add(id);
          }
        }
      }

      result = newResult;
    }

    if (result == null) return new TIntHashSet();
    return result;
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

  @NotNull
  public static File getStorageFile(@NotNull String kind, @NotNull String id, int version) {
    File subdir = new File(PersistentUtil.LOG_CACHE, kind);
    String safeLogId = PathUtilRt.suggestFileName(id, true, true);
    return new File(subdir, safeLogId + "." + version);
  }

  private class MyMapReduceIndex extends MapReduceIndex<Integer, Void, VcsFullCommitDetails> {

    public MyMapReduceIndex(@NotNull DataIndexer<Integer, Void, VcsFullCommitDetails> indexer, int version) throws IOException {
      super(new MyIndexExtension(indexer, version),
            new MapIndexStorage<>(getStorageFile("index-" + myName, VcsLogFullDetailsIndex.this.myLogId, version),
                                  EnumeratorIntegerDescriptor.INSTANCE,
                                  ScalarIndexExtension.VOID_DATA_EXTERNALIZER, 5000));
    }

    public boolean isIndexed(int commitId) throws IOException {
      return myInputsIndex.containsMapping(commitId);
    }

    @Override
    protected PersistentHashMap<Integer, Collection<Integer>> createInputsIndex() throws IOException {
      IndexExtension<Integer, Void, VcsFullCommitDetails> extension = getExtension();
      return new PersistentHashMap<>(PersistentUtil.getStorageFile("index-inputs-" + myName, myLogId, extension.getVersion()),
                                     EnumeratorIntegerDescriptor.INSTANCE,
                                     new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), myID));
    }
  }

  private class MyIndexExtension extends IndexExtension<Integer, Void, VcsFullCommitDetails> {
    @NotNull private final DataIndexer<Integer, Void, VcsFullCommitDetails> myIndexer;
    private final int myVersion;

    public MyIndexExtension(@NotNull DataIndexer<Integer, Void, VcsFullCommitDetails> indexer, int version) {
      myIndexer = indexer;
      myVersion = version;
    }

    @NotNull
    @Override
    public ID<Integer, Void> getName() {
      return myID;
    }

    @NotNull
    @Override
    public DataIndexer<Integer, Void, VcsFullCommitDetails> getIndexer() {
      return myIndexer;
    }

    @NotNull
    @Override
    public KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<Void> getValueExternalizer() {
      return ScalarIndexExtension.VOID_DATA_EXTERNALIZER;
    }

    @Override
    public int getVersion() {
      return myVersion;
    }
  }
}
