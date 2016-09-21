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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class VcsLogMessagesTrigramIndex extends VcsLogFullDetailsIndex<Void> {
  private static final Logger LOG = Logger.getInstance(VcsLogMessagesTrigramIndex.class);
  private static final String TRIGRAMS = "trigrams";
  private static final int VALUE = 239;

  @NotNull private final PersistentHashMap<Integer, Integer> myNoTrigramsCommits;

  public VcsLogMessagesTrigramIndex(@NotNull String logId, @NotNull Disposable disposableParent) throws IOException {
    super(logId, TRIGRAMS, VcsLogPersistentIndex.getVersion(), new TrigramMessageIndexer(), ScalarIndexExtension.VOID_DATA_EXTERNALIZER,
          disposableParent);

    myNoTrigramsCommits =
      PersistentUtil.createPersistentHashMap(EnumeratorIntegerDescriptor.INSTANCE, "index-no-" + TRIGRAMS, logId,
                                             VcsLogPersistentIndex.getVersion());
  }

  @Nullable
  public ValueContainer.IntIterator getCommitsForSubstring(@NotNull String string) throws StorageException {
    MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
    TrigramBuilder.processTrigrams(string, trigramProcessor);

    if (trigramProcessor.map.isEmpty()) return null;

    return getCommitsWithAllKeys(trigramProcessor.map.keySet());
  }

  @Override
  protected void onNotIndexableCommit(int commit) throws StorageException {
    try {
      myNoTrigramsCommits.put(commit, VALUE);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public boolean isIndexed(int commit) throws IOException {
    return super.isIndexed(commit) || myNoTrigramsCommits.containsMapping(commit);
  }

  @Override
  public void flush() throws StorageException {
    super.flush();
    myNoTrigramsCommits.force();
  }

  @Override
  public void dispose() {
    super.dispose();
    try {
      myNoTrigramsCommits.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Override
  public void markCorrupted() {
    super.markCorrupted();
    myNoTrigramsCommits.markCorrupted();
  }

  @NotNull
  public String getTrigramInfo(int commit) throws IOException {
    if (myNoTrigramsCommits.containsMapping(commit)) {
      return "No trigrams";
    }

    Collection<Integer> keys = getKeysForCommit(commit);
    assert keys != null;

    StringBuilder builder = new StringBuilder();
    for (Integer key : keys) {
      builder.append((char)(key >> 16));
      builder.append((char)((key >> 8) % 256));
      builder.append((char)(key % 256));
      builder.append(" ");
    }

    return builder.toString();
  }

  public static class TrigramMessageIndexer implements DataIndexer<Integer, Void, VcsFullCommitDetails> {
    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsFullCommitDetails inputData) {
      MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
      TrigramBuilder.processTrigrams(inputData.getFullMessage(), trigramProcessor);

      return trigramProcessor.map;
    }
  }

  private static class MyTrigramProcessor extends TrigramBuilder.TrigramProcessor {
    Map<Integer, Void> map;

    @Override
    public boolean consumeTrigramsCount(int count) {
      map = new THashMap<>(count);
      return true;
    }

    @Override
    public boolean execute(int value) {
      map.put(value, null);
      return true;
    }
  }
}
