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
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.ObjIntConsumer;

import static com.intellij.vcs.log.data.index.VcsLogPersistentIndex.getVersion;

public class VcsLogFullDetailsIndex<T> implements Disposable {
  protected static final String INDEX = "index";
  @NotNull protected final MyMapReduceIndex myMapReduceIndex;
  @NotNull private final IndexId<Integer, T> myID;
  @NotNull private final String myLogId;
  @NotNull private final String myName;
  @NotNull protected final DataIndexer<Integer, T, VcsFullCommitDetails> myIndexer;
  @NotNull private final FatalErrorHandler myFatalErrorHandler;
  private volatile boolean myDisposed = false;

  public VcsLogFullDetailsIndex(@NotNull String logId,
                                @NotNull String name,
                                final int version,
                                @NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                                @NotNull DataExternalizer<T> externalizer,
                                @NotNull FatalErrorHandler fatalErrorHandler,
                                @NotNull Disposable disposableParent)
    throws IOException {
    myID = IndexId.create(name);
    myName = name;
    myLogId = logId;
    myIndexer = indexer;
    myFatalErrorHandler = fatalErrorHandler;

    myMapReduceIndex = new MyMapReduceIndex(myIndexer, externalizer, version);

    Disposer.register(disposableParent, this);
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

  protected void iterateCommitIdsAndValues(int key, @NotNull ObjIntConsumer<T> consumer) throws StorageException {
    myMapReduceIndex.getData(key).forEach((id, value) -> {
      consumer.accept(value, id);
      return true;
    });
  }

  protected boolean iterateCommitIdsAndValues(int key, @NotNull BiPredicate<T, Integer> consumer) throws StorageException {
    return myMapReduceIndex.getData(key).forEach((id, value) -> consumer.test(value, id));
  }

  public void update(int commitId, @NotNull VcsFullCommitDetails details) {
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

  private class MyMapReduceIndex extends MapReduceIndex<Integer, T, VcsFullCommitDetails> {
    public MyMapReduceIndex(@NotNull DataIndexer<Integer, T, VcsFullCommitDetails> indexer,
                            @NotNull DataExternalizer<T> externalizer,
                            int version) throws IOException {
      super(new MyIndexExtension(indexer, externalizer, version),
            new MyMapIndexStorage<>(myName, myLogId, externalizer), new EmptyForwardIndex<>());
    }

    @Override
    public void checkCanceled() {
      ProgressManager.checkCanceled();
    }

    @Override
    public void requestRebuild(@NotNull Exception ex) {
      myFatalErrorHandler.consume(this, ex);
    }
  }

  private static class MyMapIndexStorage<T> extends MapIndexStorage<Integer, T> {
    public MyMapIndexStorage(@NotNull String name, @NotNull String logId, @NotNull DataExternalizer<T> externalizer)
      throws IOException {
      super(PersistentUtil.getStorageFile(INDEX, name, logId, getVersion()), EnumeratorIntegerDescriptor.INSTANCE, externalizer, 5000, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
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
    public IndexId<Integer, T> getName() {
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
